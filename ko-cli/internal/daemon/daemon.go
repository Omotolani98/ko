package daemon

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/Omotolani98/ko/ko-cli/internal"
	"github.com/Omotolani98/ko/ko-cli/internal/dashboard"
	"github.com/Omotolani98/ko/ko-cli/internal/infra"
	"github.com/Omotolani98/ko/ko-cli/internal/model"
	"github.com/Omotolani98/ko/ko-cli/internal/style"
	"github.com/Omotolani98/ko/ko-cli/internal/watcher"
)

// State represents the daemon lifecycle state.
type State string

const (
	StateStarting     State = "starting"
	StateProvisioning State = "provisioning"
	StateRunning      State = "running"
	StateRebuilding   State = "rebuilding"
	StateShutdown     State = "shutdown"
)

// Opts configures the daemon.
type Opts struct {
	ProjectDir    string
	ModuleDir     string
	Module        string
	AppPort       int
	DashboardPort int
	Watch         bool
	Containers    bool
}

// Daemon is the central coordinator for ko run.
// It manages the app lifecycle, file watcher, infrastructure containers,
// dashboard server, and Unix socket API.
type Daemon struct {
	opts         Opts
	state        State
	mu           sync.RWMutex
	startTime    time.Time
	appPort      int
	dashboardPort int
	watchEnabled bool

	infraMgr    *InfraManager
	dashServer  *dashboard.Server
	fileWatcher *watcher.Watcher
	socketSrv   *SocketServer

	appModel  *model.AppModel
	modelJSON []byte

	rebuildCh chan struct{}
}

// New creates a new daemon with the given options.
func New(opts Opts) *Daemon {
	return &Daemon{
		opts:          opts,
		state:         StateStarting,
		startTime:     time.Now(),
		appPort:       opts.AppPort,
		dashboardPort: opts.DashboardPort,
		watchEnabled:  opts.Watch,
		rebuildCh:     make(chan struct{}, 1),
	}
}

// State returns the current daemon state.
func (d *Daemon) State() State {
	d.mu.RLock()
	defer d.mu.RUnlock()
	return d.state
}

func (d *Daemon) setState(s State) {
	d.mu.Lock()
	d.state = s
	d.mu.Unlock()
}

// Run executes the full daemon lifecycle. Blocks until ctx is cancelled.
func (d *Daemon) Run(ctx context.Context) error {
	fmt.Println(style.Banner())
	fmt.Println()
	fmt.Println(style.Info("Starting Kọ́ development server..."))
	fmt.Println()

	// Step 1: Build the project
	fmt.Println(style.Info("Building application model..."))
	buildTask := "build"
	if d.opts.Module != "" {
		buildTask = d.opts.Module + ":build"
	}
	if err := internal.RunGradle(d.opts.ProjectDir, []string{buildTask, "-x", "test"}, nil, os.Stdout, os.Stderr); err != nil {
		return fmt.Errorf("gradle build failed: %w", err)
	}
	fmt.Println(style.Ok("Build complete"))
	fmt.Println()

	// Step 2: Load the app model
	modelDir := d.opts.ModuleDir
	if modelDir == "" {
		modelDir = d.opts.ProjectDir
	}

	appModel, modelJSON, err := model.LoadAppModelRaw(modelDir)
	if err != nil {
		return fmt.Errorf("failed to load app model: %w", err)
	}
	d.mu.Lock()
	d.appModel = appModel
	d.modelJSON = modelJSON
	d.mu.Unlock()

	fmt.Println(style.Info(fmt.Sprintf("App: %s", style.Service(appModel.App))))
	fmt.Println(style.Info(fmt.Sprintf("Model: %s", appModel.Summary())))
	fmt.Println()

	// Step 3: Provision infrastructure
	var configPath string
	if d.opts.Containers && DockerAvailable() {
		d.setState(StateProvisioning)
		fmt.Println(style.Info("Provisioning infrastructure containers..."))

		d.infraMgr = NewInfraManager(appModel.App)
		if err := d.infraMgr.Provision(appModel); err != nil {
			return fmt.Errorf("failed to provision infrastructure: %w", err)
		}

		containers := d.infraMgr.Containers()
		for _, c := range containers {
			fmt.Printf("  %s %s on :%d\n", style.Tag(c.Type, style.Primary), style.Infra(c.Name), c.Port)
		}
		fmt.Println()

		configPath, err = d.infraMgr.GenerateInfraConfig(appModel, d.appPort, modelDir)
		if err != nil {
			return fmt.Errorf("failed to generate infra config: %w", err)
		}
	} else {
		d.infraMgr = NewInfraManager(appModel.App)
		if d.opts.Containers && !DockerAvailable() {
			fmt.Println(style.Warn("Docker not available — using in-memory providers"))
			fmt.Println()
		}
		configPath, err = infra.GenerateLocalConfig(appModel, modelDir)
		if err != nil {
			return fmt.Errorf("failed to generate infra config: %w", err)
		}
	}
	fmt.Println(style.Info(fmt.Sprintf("Generated %s", style.Path(configPath))))
	fmt.Println()

	// Step 4: Start dashboard server
	d.dashServer = dashboard.NewServer(modelJSON, d.appPort)
	go func() {
		if err := d.dashServer.Start(d.dashboardPort); err != nil {
			fmt.Fprintln(os.Stderr, style.Err(fmt.Sprintf("Dashboard server error: %v", err)))
		}
	}()
	fmt.Println(style.Ok(fmt.Sprintf("Dashboard at %s", style.URL(fmt.Sprintf("http://localhost:%d", d.dashboardPort)))))
	fmt.Println()

	// Step 5: Start Unix socket server
	socketSrv, err := NewSocketServer(d)
	if err != nil {
		fmt.Fprintln(os.Stderr, style.Warn(fmt.Sprintf("Socket server: %v (status command won't work)", err)))
	} else {
		d.socketSrv = socketSrv
		go d.socketSrv.Start()
		fmt.Println(style.Ok(fmt.Sprintf("Daemon socket at %s", style.Path(SocketPath()))))
		fmt.Println()
	}

	// Step 6: Start file watcher
	if d.opts.Watch {
		w, err := watcher.New(d.opts.ProjectDir, d.opts.ModuleDir, func(event watcher.ChangeEvent) {
			d.triggerRebuild()
		})
		if err != nil {
			fmt.Fprintln(os.Stderr, style.Warn(fmt.Sprintf("File watcher: %v (hot reload disabled)", err)))
		} else {
			d.fileWatcher = w
			go d.fileWatcher.Start(ctx)
			fmt.Println(style.Ok("File watcher active — save a file to trigger rebuild"))
			fmt.Println()
		}
	}

	// Step 7: Start the app with bootRun
	fmt.Println(style.Info("Starting Spring Boot app..."))
	fmt.Println()

	bootTask := "bootRun"
	if d.opts.Module != "" {
		bootTask = d.opts.Module + ":bootRun"
	}

	env := []string{
		fmt.Sprintf("KO_INFRA_CONFIG=%s", configPath),
		fmt.Sprintf("SERVER_PORT=%d", d.appPort),
	}
	bootArgs := fmt.Sprintf("--args=--server.port=%d", d.appPort)

	d.setState(StateRunning)

	errCh := make(chan error, 1)
	go func() {
		errCh <- internal.RunGradle(d.opts.ProjectDir, []string{bootTask, bootArgs}, env, os.Stdout, os.Stderr)
	}()

	elapsed := time.Since(d.startTime).Round(time.Millisecond)
	fmt.Println()
	fmt.Println(style.Ok(fmt.Sprintf("App available at %s (started in %s)",
		style.URL(fmt.Sprintf("http://localhost:%d", d.appPort)), elapsed)))
	fmt.Println()

	// Event loop
	for {
		select {
		case <-ctx.Done():
			return d.shutdown(ctx)
		case err := <-errCh:
			d.shutdown(context.Background())
			if err != nil {
				return fmt.Errorf("app exited with error: %w", err)
			}
			return nil
		case <-d.rebuildCh:
			d.handleRebuild(ctx)
		}
	}
}

func (d *Daemon) triggerRebuild() {
	select {
	case d.rebuildCh <- struct{}{}:
	default:
		// Already a rebuild pending
	}
}

func (d *Daemon) handleRebuild(ctx context.Context) {
	d.setState(StateRebuilding)
	fmt.Println()
	fmt.Println(style.Info("File change detected — rebuilding..."))

	buildTask := "build"
	if d.opts.Module != "" {
		buildTask = d.opts.Module + ":build"
	}

	if err := internal.RunGradle(d.opts.ProjectDir, []string{buildTask, "-x", "test"}, nil, os.Stdout, os.Stderr); err != nil {
		fmt.Fprintln(os.Stderr, style.Err(fmt.Sprintf("Rebuild failed: %v", err)))
		d.setState(StateRunning)
		return
	}

	// Reload model
	modelDir := d.opts.ModuleDir
	if modelDir == "" {
		modelDir = d.opts.ProjectDir
	}
	appModel, modelJSON, err := model.LoadAppModelRaw(modelDir)
	if err != nil {
		fmt.Fprintln(os.Stderr, style.Err(fmt.Sprintf("Failed to reload model: %v", err)))
		d.setState(StateRunning)
		return
	}

	d.mu.Lock()
	d.appModel = appModel
	d.modelJSON = modelJSON
	d.mu.Unlock()

	// Update dashboard with new model
	d.dashServer.UpdateModel(modelJSON)

	fmt.Println(style.Ok("Rebuild complete — app restarting via DevTools"))
	fmt.Println()
	d.setState(StateRunning)
}

func (d *Daemon) shutdown(ctx context.Context) error {
	d.setState(StateShutdown)
	fmt.Println()
	fmt.Println(style.Info("Shutting down..."))

	if d.fileWatcher != nil {
		d.fileWatcher.Stop()
	}

	if d.socketSrv != nil {
		d.socketSrv.Stop()
	}

	if d.dashServer != nil {
		d.dashServer.Shutdown(ctx)
	}

	if d.infraMgr != nil {
		if err := d.infraMgr.Shutdown(ctx); err != nil {
			fmt.Fprintln(os.Stderr, style.Warn(fmt.Sprintf("Container cleanup: %v", err)))
		}
	}

	fmt.Println(style.Ok("Shutdown complete"))
	return nil
}

// ResolveModuleDir converts a Gradle module path to a filesystem path.
func ResolveModuleDir(projectDir, module string) string {
	if module == "" {
		return ""
	}
	modPath := ""
	for _, part := range splitModule(module) {
		if part != "" {
			modPath = filepath.Join(modPath, part)
		}
	}
	return filepath.Join(projectDir, modPath)
}

func splitModule(module string) []string {
	parts := []string{}
	current := ""
	for _, c := range module {
		if c == ':' {
			if current != "" {
				parts = append(parts, current)
			}
			current = ""
		} else {
			current += string(c)
		}
	}
	if current != "" {
		parts = append(parts, current)
	}
	return parts
}

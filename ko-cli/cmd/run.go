package cmd

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"github.com/Omotolani98/ko/ko-cli/internal"
	"github.com/Omotolani98/ko/ko-cli/internal/dashboard"
	"github.com/Omotolani98/ko/ko-cli/internal/infra"
	"github.com/Omotolani98/ko/ko-cli/internal/model"
	"github.com/Omotolani98/ko/ko-cli/internal/style"
	"github.com/spf13/cobra"
)

var runCmd = &cobra.Command{
	Use:   "run",
	Short: "Build and start your app with local infrastructure",
	Long: `Build the application model, generate local infra-config.json,
and start the Spring Boot app via Gradle bootRun.

This is the main development command — run it and start coding.`,
	RunE: runApp,
}

var (
	runPort        int
	runModule      string
	dashboardPort  int
)

func init() {
	runCmd.Flags().IntVarP(&runPort, "port", "p", 8080, "HTTP port for the app")
	runCmd.Flags().StringVarP(&runModule, "module", "m", "", "Gradle module to run (e.g., :examples:hello-world)")
	runCmd.Flags().IntVar(&dashboardPort, "dashboard-port", 9400, "Port for the dev dashboard")
	rootCmd.AddCommand(runCmd)
}

func runApp(cmd *cobra.Command, args []string) error {
	startTime := time.Now()
	projectDir, err := os.Getwd()
	if err != nil {
		return fmt.Errorf("failed to get working directory: %w", err)
	}

	fmt.Println(style.Banner())
	fmt.Println()
	fmt.Println(style.Info("Starting Kọ́ development server..."))
	fmt.Println()

	// Step 1: Build the app model
	fmt.Println(style.Info("Building application model..."))

	buildTask := "build"
	if runModule != "" {
		buildTask = runModule + ":build"
	}

	err = internal.RunGradle(projectDir, []string{buildTask, "-x", "test"}, nil, os.Stdout, os.Stderr)
	if err != nil {
		return fmt.Errorf("gradle build failed: %w", err)
	}
	fmt.Println(style.Ok("Build complete"))
	fmt.Println()

	// Step 2: Load the app model
	modelDir := projectDir
	if runModule != "" {
		// Convert :examples:hello-world to examples/hello-world
		modPath := ""
		for _, part := range splitModule(runModule) {
			if part != "" {
				modPath = filepath.Join(modPath, part)
			}
		}
		modelDir = filepath.Join(projectDir, modPath)
	}

	appModel, modelJSON, err := model.LoadAppModelRaw(modelDir)
	if err != nil {
		return fmt.Errorf("failed to load app model: %w", err)
	}

	fmt.Println(style.Info(fmt.Sprintf("App: %s", style.Service(appModel.App))))
	fmt.Println(style.Info(fmt.Sprintf("Model: %s", appModel.Summary())))
	fmt.Println()

	// Print discovered infrastructure
	printInfrastructure(appModel)

	// Step 3: Generate local infra-config.json
	configPath, err := infra.GenerateLocalConfig(appModel, modelDir)
	if err != nil {
		return fmt.Errorf("failed to generate infra config: %w", err)
	}
	fmt.Println(style.Info(fmt.Sprintf("Generated %s", style.Path(configPath))))
	fmt.Println()

	// Step 4: Start the dev dashboard
	dashServer := dashboard.NewServer(modelJSON, runPort)
	go func() {
		if err := dashServer.Start(dashboardPort); err != nil {
			fmt.Fprintln(os.Stderr, style.Err(fmt.Sprintf("Dashboard server error: %v", err)))
		}
	}()
	fmt.Println(style.Ok(fmt.Sprintf("Dashboard at %s", style.URL(fmt.Sprintf("http://localhost:%d", dashboardPort)))))
	fmt.Println()

	// Step 5: Start the app with bootRun
	fmt.Println(style.Info("Starting Spring Boot app..."))
	fmt.Println()

	bootTask := "bootRun"
	if runModule != "" {
		bootTask = runModule + ":bootRun"
	}

	env := []string{
		fmt.Sprintf("KO_INFRA_CONFIG=%s", configPath),
		fmt.Sprintf("SERVER_PORT=%d", runPort),
	}

	bootArgs := fmt.Sprintf("--args=--server.port=%d", runPort)

	// Handle graceful shutdown
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	errCh := make(chan error, 1)
	go func() {
		errCh <- internal.RunGradle(projectDir, []string{bootTask, bootArgs}, env, os.Stdout, os.Stderr)
	}()

	elapsed := time.Since(startTime).Round(time.Millisecond)
	fmt.Println()
	fmt.Println(style.Ok(fmt.Sprintf("App available at %s (started in %s)",
		style.URL(fmt.Sprintf("http://localhost:%d", runPort)), elapsed)))
	fmt.Println()

	select {
	case err := <-errCh:
		if err != nil {
			return fmt.Errorf("app exited with error: %w", err)
		}
		return nil
	case sig := <-sigCh:
		fmt.Println()
		fmt.Println(style.Info(fmt.Sprintf("Received %s, shutting down...", sig)))
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		dashServer.Shutdown(ctx)
		return nil
	}
}

func printInfrastructure(m *model.AppModel) {
	for _, svc := range m.Services {
		fmt.Printf("  %s %s\n", style.Tag("service", style.Secondary), style.Service(svc.Name))

		for _, api := range svc.APIs {
			method := style.Bold.Render(api.Method)
			authTag := ""
			if api.Auth {
				authTag = " " + style.Tag("auth", style.Warning)
			}
			fmt.Printf("    %s %s%s\n", method, api.Path, authTag)
		}

		for _, db := range svc.Databases {
			fmt.Printf("    %s %s\n", style.Tag("db", style.Primary), style.Infra(db.Name))
		}
		for _, c := range svc.Caches {
			fmt.Printf("    %s %s\n", style.Tag("cache", style.Primary), style.Infra(c.Name))
		}
		for _, b := range svc.Buckets {
			fmt.Printf("    %s %s\n", style.Tag("bucket", style.Primary), style.Infra(b.Name))
		}
		for _, cj := range svc.CronJobs {
			fmt.Printf("    %s %s (%s)\n", style.Tag("cron", style.Primary), style.Infra(cj.Name), cj.Schedule)
		}
		fmt.Println()
	}

	if len(m.PubSubTopics) > 0 {
		for _, t := range m.PubSubTopics {
			fmt.Printf("  %s %s → %d subscriber(s)\n",
				style.Tag("topic", style.Primary), style.Infra(t.Name), len(t.Subscribers))
		}
		fmt.Println()
	}
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

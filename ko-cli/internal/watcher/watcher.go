package watcher

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/fsnotify/fsnotify"
)

// ChangeType classifies what kind of source change occurred.
type ChangeType int

const (
	// SourceChange indicates .java source files changed — triggers gradle build -x test.
	SourceChange ChangeType = iota
	// ResourceChange indicates .properties/.yaml/.sql files changed.
	ResourceChange
	// BuildConfigChange indicates build.gradle.kts or gradle.properties changed.
	BuildConfigChange
)

// ChangeEvent represents a debounced batch of file changes.
type ChangeEvent struct {
	Type      ChangeType
	Files     []string
	Timestamp time.Time
}

// Watcher watches project source files and fires a callback on changes.
type Watcher struct {
	projectDir string
	moduleDir  string
	fsWatcher  *fsnotify.Watcher
	onChange   func(ChangeEvent)
	debounce   time.Duration
	stopCh     chan struct{}
	mu         sync.Mutex
	pending    map[string]ChangeType
	timer      *time.Timer
}

// New creates a file watcher for the given project.
// onChange is called (debounced) whenever relevant source files change.
func New(projectDir, moduleDir string, onChange func(ChangeEvent)) (*Watcher, error) {
	fsw, err := fsnotify.NewWatcher()
	if err != nil {
		return nil, err
	}
	return &Watcher{
		projectDir: projectDir,
		moduleDir:  moduleDir,
		fsWatcher:  fsw,
		onChange:   onChange,
		debounce:   500 * time.Millisecond,
		stopCh:     make(chan struct{}),
		pending:    make(map[string]ChangeType),
	}, nil
}

// Start begins watching for file changes. Blocks until ctx is cancelled or Stop is called.
func (w *Watcher) Start(ctx context.Context) error {
	// Determine which directory to watch
	watchRoot := w.moduleDir
	if watchRoot == "" {
		watchRoot = w.projectDir
	}

	// Add source directories recursively
	srcDir := filepath.Join(watchRoot, "src")
	if info, err := os.Stat(srcDir); err == nil && info.IsDir() {
		if err := w.addDirRecursive(srcDir); err != nil {
			return err
		}
	}

	// Watch build config files in project root
	w.fsWatcher.Add(watchRoot)
	w.fsWatcher.Add(w.projectDir)

	for {
		select {
		case <-ctx.Done():
			w.flush()
			return ctx.Err()
		case <-w.stopCh:
			w.flush()
			return nil
		case event, ok := <-w.fsWatcher.Events:
			if !ok {
				return nil
			}
			w.handleEvent(event)
		case err, ok := <-w.fsWatcher.Errors:
			if !ok {
				return nil
			}
			// Log but continue watching
			_ = err
		}
	}
}

// Stop signals the watcher to stop.
func (w *Watcher) Stop() {
	close(w.stopCh)
	w.fsWatcher.Close()
}

func (w *Watcher) handleEvent(event fsnotify.Event) {
	// Only care about writes, creates, and removes
	if !event.Has(fsnotify.Write) && !event.Has(fsnotify.Create) && !event.Has(fsnotify.Remove) {
		return
	}

	// If a new directory is created, start watching it
	if event.Has(fsnotify.Create) {
		if info, err := os.Stat(event.Name); err == nil && info.IsDir() {
			w.addDirRecursive(event.Name)
			return
		}
	}

	ct, relevant := classifyFile(event.Name)
	if !relevant {
		return
	}

	w.mu.Lock()
	defer w.mu.Unlock()

	// Upgrade priority: BuildConfigChange > ResourceChange > SourceChange
	if existing, ok := w.pending[event.Name]; ok {
		if ct > existing {
			w.pending[event.Name] = ct
		}
	} else {
		w.pending[event.Name] = ct
	}

	// Reset debounce timer
	if w.timer != nil {
		w.timer.Stop()
	}
	w.timer = time.AfterFunc(w.debounce, w.flush)
}

func (w *Watcher) flush() {
	w.mu.Lock()
	defer w.mu.Unlock()

	if len(w.pending) == 0 {
		return
	}

	// Find highest priority change type
	maxType := SourceChange
	files := make([]string, 0, len(w.pending))
	for f, ct := range w.pending {
		files = append(files, f)
		if ct > maxType {
			maxType = ct
		}
	}

	event := ChangeEvent{
		Type:      maxType,
		Files:     files,
		Timestamp: time.Now(),
	}

	// Clear pending
	w.pending = make(map[string]ChangeType)
	w.timer = nil

	// Fire callback (non-blocking)
	go w.onChange(event)
}

func (w *Watcher) addDirRecursive(root string) error {
	return filepath.Walk(root, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil // skip inaccessible paths
		}
		if info.IsDir() {
			name := info.Name()
			// Skip hidden dirs, build output, and node_modules
			if strings.HasPrefix(name, ".") || name == "build" || name == "node_modules" || name == "target" {
				return filepath.SkipDir
			}
			return w.fsWatcher.Add(path)
		}
		return nil
	})
}

// classifyFile determines the change type for a file, and whether it's relevant.
func classifyFile(path string) (ChangeType, bool) {
	base := filepath.Base(path)
	ext := filepath.Ext(path)

	// Build config files
	switch base {
	case "build.gradle.kts", "build.gradle", "settings.gradle.kts", "settings.gradle", "gradle.properties":
		return BuildConfigChange, true
	}

	// Source files
	switch ext {
	case ".java", ".kt", ".scala":
		return SourceChange, true
	case ".properties", ".yaml", ".yml", ".xml":
		return ResourceChange, true
	case ".sql":
		return ResourceChange, true
	}

	return SourceChange, false
}

package watcher

import (
	"context"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"
)

func TestClassifyFile(t *testing.T) {
	tests := []struct {
		path     string
		wantType ChangeType
		wantOk   bool
	}{
		{"src/main/java/Foo.java", SourceChange, true},
		{"src/main/kotlin/Bar.kt", SourceChange, true},
		{"src/main/scala/Baz.scala", SourceChange, true},
		{"src/main/resources/app.properties", ResourceChange, true},
		{"src/main/resources/app.yaml", ResourceChange, true},
		{"src/main/resources/app.yml", ResourceChange, true},
		{"db/migrations/V1__init.sql", ResourceChange, true},
		{"build.gradle.kts", BuildConfigChange, true},
		{"build.gradle", BuildConfigChange, true},
		{"settings.gradle.kts", BuildConfigChange, true},
		{"gradle.properties", BuildConfigChange, true},
		{"README.md", SourceChange, false},
		{"src/main/java/.DS_Store", SourceChange, false},
		{"package.json", SourceChange, false},
	}

	for _, tt := range tests {
		t.Run(tt.path, func(t *testing.T) {
			gotType, gotOk := classifyFile(tt.path)
			if gotOk != tt.wantOk {
				t.Errorf("classifyFile(%q) relevant = %v, want %v", tt.path, gotOk, tt.wantOk)
			}
			if gotOk && gotType != tt.wantType {
				t.Errorf("classifyFile(%q) type = %v, want %v", tt.path, gotType, tt.wantType)
			}
		})
	}
}

func TestWatcherDebounce(t *testing.T) {
	// Create a temp directory with a Java file
	tmpDir := t.TempDir()
	srcDir := filepath.Join(tmpDir, "src", "main", "java")
	os.MkdirAll(srcDir, 0755)

	javaFile := filepath.Join(srcDir, "Test.java")
	os.WriteFile(javaFile, []byte("class Test {}"), 0644)

	var mu sync.Mutex
	var events []ChangeEvent

	w, err := New(tmpDir, "", func(event ChangeEvent) {
		mu.Lock()
		events = append(events, event)
		mu.Unlock()
	})
	if err != nil {
		t.Fatalf("New() error: %v", err)
	}
	w.debounce = 100 * time.Millisecond

	ctx, cancel := context.WithCancel(context.Background())
	go w.Start(ctx)

	// Wait for watcher to initialize
	time.Sleep(200 * time.Millisecond)

	// Write to the file multiple times rapidly
	for i := 0; i < 5; i++ {
		os.WriteFile(javaFile, []byte("class Test { // change "+string(rune('0'+i))+" }"), 0644)
		time.Sleep(20 * time.Millisecond)
	}

	// Wait for debounce to fire
	time.Sleep(300 * time.Millisecond)

	cancel()
	w.Stop()

	mu.Lock()
	defer mu.Unlock()

	// Should have received exactly 1 debounced event (not 5)
	if len(events) != 1 {
		t.Errorf("expected 1 debounced event, got %d", len(events))
		return
	}

	if events[0].Type != SourceChange {
		t.Errorf("expected SourceChange, got %v", events[0].Type)
	}
}

func TestWatcherDetectsNewFile(t *testing.T) {
	tmpDir := t.TempDir()
	srcDir := filepath.Join(tmpDir, "src", "main", "java")
	os.MkdirAll(srcDir, 0755)

	var mu sync.Mutex
	var events []ChangeEvent

	w, err := New(tmpDir, "", func(event ChangeEvent) {
		mu.Lock()
		events = append(events, event)
		mu.Unlock()
	})
	if err != nil {
		t.Fatalf("New() error: %v", err)
	}
	w.debounce = 100 * time.Millisecond

	ctx, cancel := context.WithCancel(context.Background())
	go w.Start(ctx)

	time.Sleep(200 * time.Millisecond)

	// Create a new Java file
	newFile := filepath.Join(srcDir, "NewService.java")
	os.WriteFile(newFile, []byte("class NewService {}"), 0644)

	time.Sleep(300 * time.Millisecond)

	cancel()
	w.Stop()

	mu.Lock()
	defer mu.Unlock()

	if len(events) == 0 {
		t.Error("expected at least 1 event for new file creation")
	}
}

func TestWatcherBuildConfigPriority(t *testing.T) {
	tmpDir := t.TempDir()
	srcDir := filepath.Join(tmpDir, "src", "main", "java")
	os.MkdirAll(srcDir, 0755)

	// Create initial files
	javaFile := filepath.Join(srcDir, "Test.java")
	os.WriteFile(javaFile, []byte("class Test {}"), 0644)
	gradleFile := filepath.Join(tmpDir, "build.gradle.kts")
	os.WriteFile(gradleFile, []byte("plugins {}"), 0644)

	var mu sync.Mutex
	var events []ChangeEvent

	w, err := New(tmpDir, "", func(event ChangeEvent) {
		mu.Lock()
		events = append(events, event)
		mu.Unlock()
	})
	if err != nil {
		t.Fatalf("New() error: %v", err)
	}
	w.debounce = 100 * time.Millisecond

	ctx, cancel := context.WithCancel(context.Background())
	go w.Start(ctx)

	time.Sleep(200 * time.Millisecond)

	// Modify both a Java file and build.gradle.kts within the debounce window
	os.WriteFile(javaFile, []byte("class Test { int x; }"), 0644)
	time.Sleep(20 * time.Millisecond)
	os.WriteFile(gradleFile, []byte("plugins { id(\"java\") }"), 0644)

	time.Sleep(300 * time.Millisecond)

	cancel()
	w.Stop()

	mu.Lock()
	defer mu.Unlock()

	if len(events) == 0 {
		t.Fatal("expected at least 1 event")
	}

	// The event should be BuildConfigChange (highest priority)
	if events[0].Type != BuildConfigChange {
		t.Errorf("expected BuildConfigChange (highest priority), got %v", events[0].Type)
	}
}

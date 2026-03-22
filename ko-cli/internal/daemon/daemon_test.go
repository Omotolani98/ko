package daemon

import (
	"testing"
)

func TestResolveModuleDir(t *testing.T) {
	tests := []struct {
		projectDir string
		module     string
		want       string
	}{
		{"/home/user/project", "", ""},
		{"/home/user/project", ":app", "/home/user/project/app"},
		{"/home/user/project", ":examples:hello-world", "/home/user/project/examples/hello-world"},
		{"/home/user/project", "app", "/home/user/project/app"},
	}

	for _, tt := range tests {
		t.Run(tt.module, func(t *testing.T) {
			got := ResolveModuleDir(tt.projectDir, tt.module)
			if got != tt.want {
				t.Errorf("ResolveModuleDir(%q, %q) = %q, want %q", tt.projectDir, tt.module, got, tt.want)
			}
		})
	}
}

func TestSplitModule(t *testing.T) {
	tests := []struct {
		input string
		want  []string
	}{
		{"", nil},
		{":app", []string{"app"}},
		{":examples:hello-world", []string{"examples", "hello-world"}},
		{"single", []string{"single"}},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			got := splitModule(tt.input)
			if len(got) != len(tt.want) {
				t.Errorf("splitModule(%q) = %v, want %v", tt.input, got, tt.want)
				return
			}
			for i := range got {
				if got[i] != tt.want[i] {
					t.Errorf("splitModule(%q)[%d] = %q, want %q", tt.input, i, got[i], tt.want[i])
				}
			}
		})
	}
}

func TestDaemonState(t *testing.T) {
	d := New(Opts{
		ProjectDir:    "/tmp/test",
		AppPort:       8080,
		DashboardPort: 9400,
	})

	if d.State() != StateStarting {
		t.Errorf("initial state = %s, want starting", d.State())
	}

	d.setState(StateRunning)
	if d.State() != StateRunning {
		t.Errorf("state after set = %s, want running", d.State())
	}

	d.setState(StateRebuilding)
	if d.State() != StateRebuilding {
		t.Errorf("state = %s, want rebuilding", d.State())
	}
}

func TestDaemonNew(t *testing.T) {
	opts := Opts{
		ProjectDir:    "/tmp/project",
		ModuleDir:     "/tmp/project/app",
		Module:        ":app",
		AppPort:       9000,
		DashboardPort: 9400,
		Watch:         true,
		Containers:    false,
	}

	d := New(opts)

	if d.appPort != 9000 {
		t.Errorf("appPort = %d, want 9000", d.appPort)
	}
	if d.dashboardPort != 9400 {
		t.Errorf("dashboardPort = %d, want 9400", d.dashboardPort)
	}
	if !d.watchEnabled {
		t.Error("watchEnabled should be true")
	}
	if d.opts.Containers {
		t.Error("Containers should be false")
	}
}

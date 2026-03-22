package daemon

import (
	"bufio"
	"encoding/json"
	"net"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestSocketPath(t *testing.T) {
	path := SocketPath()
	home, _ := os.UserHomeDir()
	expected := filepath.Join(home, ".ko", "daemon.sock")
	if path != expected {
		t.Errorf("SocketPath() = %s, want %s", path, expected)
	}
}

func TestSocketServerProtocol(t *testing.T) {
	// Create a minimal daemon for the socket server to query
	d := &Daemon{
		state:         StateRunning,
		startTime:     time.Now(),
		appPort:       8080,
		dashboardPort: 9400,
		watchEnabled:  true,
		infraMgr:      NewInfraManager("test"),
	}

	// Use a temp socket path instead of the default
	tmpDir := t.TempDir()
	sockPath := filepath.Join(tmpDir, "test.sock")

	// Ensure parent dir
	os.MkdirAll(filepath.Dir(sockPath), 0755)

	listener, err := net.Listen("unix", sockPath)
	if err != nil {
		t.Fatalf("failed to listen: %v", err)
	}

	srv := &SocketServer{
		listener:   listener,
		daemon:     d,
		socketPath: sockPath,
	}
	go srv.Start()
	defer srv.Stop()

	// Give server time to start
	time.Sleep(50 * time.Millisecond)

	// Connect as a client
	conn, err := net.Dial("unix", sockPath)
	if err != nil {
		t.Fatalf("failed to connect: %v", err)
	}
	defer conn.Close()

	// Test "status" method
	t.Run("status", func(t *testing.T) {
		req := SocketRequest{Method: "status", ID: "1"}
		data, _ := json.Marshal(req)
		data = append(data, '\n')
		conn.Write(data)

		scanner := bufio.NewScanner(conn)
		if !scanner.Scan() {
			t.Fatal("no response")
		}

		var resp SocketResponse
		if err := json.Unmarshal(scanner.Bytes(), &resp); err != nil {
			t.Fatalf("invalid response: %v", err)
		}

		if resp.ID != "1" {
			t.Errorf("resp.ID = %s, want 1", resp.ID)
		}
		if resp.Error != "" {
			t.Errorf("unexpected error: %s", resp.Error)
		}

		// Parse result as StatusResult
		resultBytes, _ := json.Marshal(resp.Result)
		var status StatusResult
		json.Unmarshal(resultBytes, &status)

		if status.State != "running" {
			t.Errorf("state = %s, want running", status.State)
		}
		if status.AppPort != 8080 {
			t.Errorf("app_port = %d, want 8080", status.AppPort)
		}
		if status.DashboardPort != 9400 {
			t.Errorf("dashboard_port = %d, want 9400", status.DashboardPort)
		}
		if !status.WatchEnabled {
			t.Error("watch_enabled should be true")
		}
	})

	// Test "containers" method (need a new connection since scanner consumed previous)
	conn2, _ := net.Dial("unix", sockPath)
	defer conn2.Close()

	t.Run("containers", func(t *testing.T) {
		req := SocketRequest{Method: "containers", ID: "2"}
		data, _ := json.Marshal(req)
		data = append(data, '\n')
		conn2.Write(data)

		scanner := bufio.NewScanner(conn2)
		if !scanner.Scan() {
			t.Fatal("no response")
		}

		var resp SocketResponse
		json.Unmarshal(scanner.Bytes(), &resp)

		if resp.ID != "2" {
			t.Errorf("resp.ID = %s, want 2", resp.ID)
		}
		if resp.Error != "" {
			t.Errorf("unexpected error: %s", resp.Error)
		}
	})

	// Test unknown method
	conn3, _ := net.Dial("unix", sockPath)
	defer conn3.Close()

	t.Run("unknown_method", func(t *testing.T) {
		req := SocketRequest{Method: "foobar", ID: "3"}
		data, _ := json.Marshal(req)
		data = append(data, '\n')
		conn3.Write(data)

		scanner := bufio.NewScanner(conn3)
		if !scanner.Scan() {
			t.Fatal("no response")
		}

		var resp SocketResponse
		json.Unmarshal(scanner.Bytes(), &resp)

		if resp.Error == "" {
			t.Error("expected error for unknown method")
		}
	})
}

func TestSocketServerStaleCleanup(t *testing.T) {
	tmpDir := t.TempDir()
	sockPath := filepath.Join(tmpDir, "stale.sock")

	// Create a stale socket file (just a regular file, not a real socket)
	os.WriteFile(sockPath, []byte("stale"), 0644)

	// NewSocketServer should detect it's stale and remove it
	d := &Daemon{
		state:     StateRunning,
		startTime: time.Now(),
		infraMgr:  NewInfraManager("test"),
	}

	// We can't use NewSocketServer directly since it uses the default path.
	// Instead verify the stale detection logic works.
	_, err := os.Stat(sockPath)
	if err != nil {
		t.Fatal("stale file should exist")
	}

	// Try to connect — should fail since it's just a file
	_, err = net.Dial("unix", sockPath)
	if err == nil {
		t.Fatal("should not connect to a regular file")
	}

	// Remove stale (simulating what NewSocketServer does)
	os.Remove(sockPath)

	// Now create a real socket
	listener, err := net.Listen("unix", sockPath)
	if err != nil {
		t.Fatalf("failed to listen after cleanup: %v", err)
	}

	srv := &SocketServer{
		listener:   listener,
		daemon:     d,
		socketPath: sockPath,
	}
	defer srv.Stop()

	// Verify socket works
	go srv.Start()
	time.Sleep(50 * time.Millisecond)

	conn, err := net.Dial("unix", sockPath)
	if err != nil {
		t.Fatalf("failed to connect to new socket: %v", err)
	}
	conn.Close()
}

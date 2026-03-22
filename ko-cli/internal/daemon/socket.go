package daemon

import (
	"bufio"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"sync"
	"time"
)

// SocketRequest is a JSON request sent over the Unix socket.
type SocketRequest struct {
	Method string `json:"method"`
	ID     string `json:"id"`
}

// SocketResponse is a JSON response sent back over the Unix socket.
type SocketResponse struct {
	ID     string `json:"id"`
	Result any    `json:"result,omitempty"`
	Error  string `json:"error,omitempty"`
}

// StatusResult is returned by the "status" method.
type StatusResult struct {
	State         string      `json:"state"`
	Uptime        string      `json:"uptime"`
	AppPort       int         `json:"app_port"`
	DashboardPort int         `json:"dashboard_port"`
	Containers    []Container `json:"containers"`
	WatchEnabled  bool        `json:"watch_enabled"`
}

// SocketServer serves a JSON-line protocol over a Unix domain socket.
type SocketServer struct {
	listener   net.Listener
	daemon     *Daemon
	socketPath string
	mu         sync.Mutex
	conns      []net.Conn
}

// SocketPath returns the default daemon socket path.
func SocketPath() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".ko", "daemon.sock")
}

// NewSocketServer creates a new socket server bound to the daemon.
func NewSocketServer(d *Daemon) (*SocketServer, error) {
	sockPath := SocketPath()

	// Ensure ~/.ko/ directory exists
	if err := os.MkdirAll(filepath.Dir(sockPath), 0755); err != nil {
		return nil, fmt.Errorf("failed to create socket directory: %w", err)
	}

	// Check for stale socket
	if _, err := os.Stat(sockPath); err == nil {
		conn, err := net.Dial("unix", sockPath)
		if err == nil {
			conn.Close()
			return nil, fmt.Errorf("another ko daemon is already running (socket: %s)", sockPath)
		}
		// Stale socket — remove it
		os.Remove(sockPath)
	}

	listener, err := net.Listen("unix", sockPath)
	if err != nil {
		return nil, fmt.Errorf("failed to listen on socket: %w", err)
	}

	return &SocketServer{
		listener:   listener,
		daemon:     d,
		socketPath: sockPath,
	}, nil
}

// Start begins accepting connections. Blocks until Stop is called.
func (s *SocketServer) Start() error {
	for {
		conn, err := s.listener.Accept()
		if err != nil {
			// listener closed
			return nil
		}
		s.mu.Lock()
		s.conns = append(s.conns, conn)
		s.mu.Unlock()
		go s.handleConn(conn)
	}
}

// Stop closes the listener and all connections, and removes the socket file.
func (s *SocketServer) Stop() error {
	s.listener.Close()
	s.mu.Lock()
	for _, c := range s.conns {
		c.Close()
	}
	s.conns = nil
	s.mu.Unlock()
	os.Remove(s.socketPath)
	return nil
}

func (s *SocketServer) handleConn(conn net.Conn) {
	defer conn.Close()
	scanner := bufio.NewScanner(conn)
	for scanner.Scan() {
		var req SocketRequest
		if err := json.Unmarshal(scanner.Bytes(), &req); err != nil {
			s.writeResponse(conn, SocketResponse{Error: "invalid json"})
			continue
		}
		resp := s.dispatch(req)
		s.writeResponse(conn, resp)
	}
}

func (s *SocketServer) dispatch(req SocketRequest) SocketResponse {
	switch req.Method {
	case "status":
		return SocketResponse{
			ID: req.ID,
			Result: StatusResult{
				State:         string(s.daemon.State()),
				Uptime:        time.Since(s.daemon.startTime).Round(time.Second).String(),
				AppPort:       s.daemon.appPort,
				DashboardPort: s.daemon.dashboardPort,
				Containers:    s.daemon.infraMgr.Containers(),
				WatchEnabled:  s.daemon.watchEnabled,
			},
		}
	case "model":
		s.daemon.mu.RLock()
		modelJSON := s.daemon.modelJSON
		s.daemon.mu.RUnlock()
		return SocketResponse{
			ID:     req.ID,
			Result: json.RawMessage(modelJSON),
		}
	case "containers":
		return SocketResponse{
			ID:     req.ID,
			Result: s.daemon.infraMgr.Containers(),
		}
	case "rebuild":
		go s.daemon.triggerRebuild()
		return SocketResponse{
			ID:     req.ID,
			Result: map[string]string{"status": "rebuild triggered"},
		}
	default:
		return SocketResponse{
			ID:    req.ID,
			Error: fmt.Sprintf("unknown method: %s", req.Method),
		}
	}
}

func (s *SocketServer) writeResponse(conn net.Conn, resp SocketResponse) {
	data, _ := json.Marshal(resp)
	data = append(data, '\n')
	conn.Write(data)
}

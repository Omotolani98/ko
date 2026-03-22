package dashboard

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// Server serves the Ko dev dashboard and API endpoints.
type Server struct {
	modelJSON  []byte
	appPort    int
	startTime  time.Time
	httpServer *http.Server
}

// NewServer creates a new dashboard server.
func NewServer(modelJSON []byte, appPort int) *Server {
	return &Server{
		modelJSON: modelJSON,
		appPort:   appPort,
		startTime: time.Now(),
	}
}

// Start begins serving on the given port. Blocks until shutdown.
func (s *Server) Start(port int) error {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/model", s.handleModel)
	mux.HandleFunc("/api/health", s.handleHealth)
	mux.HandleFunc("/api/proxy/", s.handleProxy)
	mux.Handle("/", spaHandler())

	s.httpServer = &http.Server{
		Addr:    fmt.Sprintf(":%d", port),
		Handler: mux,
	}

	if err := s.httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		return fmt.Errorf("dashboard server error: %w", err)
	}
	return nil
}

// Shutdown gracefully stops the server.
func (s *Server) Shutdown(ctx context.Context) error {
	if s.httpServer != nil {
		return s.httpServer.Shutdown(ctx)
	}
	return nil
}

func (s *Server) handleModel(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Write(s.modelJSON)
}

func (s *Server) handleHealth(w http.ResponseWriter, _ *http.Request) {
	uptime := time.Since(s.startTime).Round(time.Second).String()
	resp := map[string]interface{}{
		"status":   "ok",
		"app_port": s.appPort,
		"uptime":   uptime,
	}
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	json.NewEncoder(w).Encode(resp)
}

func (s *Server) handleProxy(w http.ResponseWriter, r *http.Request) {
	// Allow CORS preflight
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS")
	w.Header().Set("Access-Control-Allow-Headers", "Content-Type, X-Ko-Method")
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusNoContent)
		return
	}

	// Extract target path (everything after /api/proxy)
	targetPath := strings.TrimPrefix(r.URL.Path, "/api/proxy")
	if targetPath == "" {
		targetPath = "/"
	}

	// Get the actual HTTP method from header, default to request method
	method := r.Header.Get("X-Ko-Method")
	if method == "" {
		method = r.Method
	}

	targetURL := fmt.Sprintf("http://localhost:%d%s", s.appPort, targetPath)

	// Forward the request
	client := &http.Client{Timeout: 30 * time.Second}
	proxyReq, err := http.NewRequestWithContext(r.Context(), method, targetURL, r.Body)
	if err != nil {
		http.Error(w, "Failed to create proxy request", http.StatusInternalServerError)
		return
	}

	// Forward content type
	if ct := r.Header.Get("Content-Type"); ct != "" {
		proxyReq.Header.Set("Content-Type", ct)
	}
	proxyReq.Header.Set("Accept", "application/json")

	resp, err := client.Do(proxyReq)
	if err != nil {
		w.Header().Set("Content-Type", "text/plain")
		w.WriteHeader(http.StatusBadGateway)
		fmt.Fprintf(w, "Proxy error: %v", err)
		return
	}
	defer resp.Body.Close()

	// Copy response headers
	for k, vals := range resp.Header {
		for _, v := range vals {
			w.Header().Add(k, v)
		}
	}
	w.WriteHeader(resp.StatusCode)
	io.Copy(w, resp.Body)
}

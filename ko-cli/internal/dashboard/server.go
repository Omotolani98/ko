package dashboard

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/Omotolani98/ko/ko-cli/internal/traces"
)

// Server serves the Ko dev dashboard and API endpoints.
type Server struct {
	mu         sync.RWMutex
	modelJSON  []byte
	appPort    int
	startTime  time.Time
	httpServer *http.Server
	traceStore *traces.TraceStore
}

// NewServer creates a new dashboard server.
func NewServer(modelJSON []byte, appPort int, traceStore *traces.TraceStore) *Server {
	return &Server{
		modelJSON:  modelJSON,
		appPort:    appPort,
		startTime:  time.Now(),
		traceStore: traceStore,
	}
}

// Start begins serving on the given port. Blocks until shutdown.
func (s *Server) Start(port int) error {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/model", s.handleModel)
	mux.HandleFunc("/api/health", s.handleHealth)
	mux.HandleFunc("/api/proxy/", s.handleProxy)
	mux.HandleFunc("/api/traces/ingest", s.handleIngestTraces)
	mux.HandleFunc("/api/traces/", s.handleGetTrace)
	mux.HandleFunc("/api/traces", s.handleListTraces)
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

// UpdateModel replaces the model JSON served by the dashboard.
// Safe to call from any goroutine.
func (s *Server) UpdateModel(modelJSON []byte) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.modelJSON = modelJSON
}

func (s *Server) handleModel(w http.ResponseWriter, _ *http.Request) {
	s.mu.RLock()
	data := s.modelJSON
	s.mu.RUnlock()
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Write(data)
}

func (s *Server) handleHealth(w http.ResponseWriter, _ *http.Request) {
	uptime := time.Since(s.startTime).Round(time.Second).String()
	resp := map[string]any{
		"status":   "ok",
		"app_port": s.appPort,
		"uptime":   uptime,
	}
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	json.NewEncoder(w).Encode(resp)
}

func (s *Server) handleProxy(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS")
	w.Header().Set("Access-Control-Allow-Headers", "Content-Type, X-Ko-Method")
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusNoContent)
		return
	}

	targetPath := strings.TrimPrefix(r.URL.Path, "/api/proxy")
	if targetPath == "" {
		targetPath = "/"
	}

	method := r.Header.Get("X-Ko-Method")
	if method == "" {
		method = r.Method
	}

	targetURL := fmt.Sprintf("http://localhost:%d%s", s.appPort, targetPath)

	client := &http.Client{Timeout: 30 * time.Second}
	proxyReq, err := http.NewRequestWithContext(r.Context(), method, targetURL, r.Body)
	if err != nil {
		http.Error(w, "Failed to create proxy request", http.StatusInternalServerError)
		return
	}

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

	for k, vals := range resp.Header {
		for _, v := range vals {
			w.Header().Add(k, v)
		}
	}
	w.WriteHeader(resp.StatusCode)
	io.Copy(w, resp.Body)
}

// handleIngestTraces receives spans from the Java runtime via POST.
func (s *Server) handleIngestTraces(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Methods", "POST, OPTIONS")
	w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusNoContent)
		return
	}
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var body struct {
		Spans []traces.Span `json:"spans"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	if s.traceStore != nil && len(body.Spans) > 0 {
		s.traceStore.Ingest(body.Spans)
	}

	w.WriteHeader(http.StatusNoContent)
}

// handleListTraces returns recent trace summaries.
func (s *Server) handleListTraces(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	limit := 50
	if l := r.URL.Query().Get("limit"); l != "" {
		if n, err := strconv.Atoi(l); err == nil && n > 0 {
			limit = n
		}
	}

	traces := make([]traces.TraceSummary, 0)
	if s.traceStore != nil {
		traces = s.traceStore.List(limit)
	}

	json.NewEncoder(w).Encode(map[string]any{
		"traces": traces,
	})
}

// handleGetTrace returns a single trace with all spans.
func (s *Server) handleGetTrace(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	// Extract trace ID from /api/traces/{id}
	traceID := strings.TrimPrefix(r.URL.Path, "/api/traces/")
	if traceID == "" || traceID == "ingest" {
		// Don't handle /api/traces/ingest here
		http.NotFound(w, r)
		return
	}

	if s.traceStore == nil {
		http.NotFound(w, r)
		return
	}

	trace := s.traceStore.Get(traceID)
	if trace == nil {
		http.NotFound(w, r)
		return
	}

	json.NewEncoder(w).Encode(trace)
}

package traces

import (
	"sync"
)

const maxTraces = 1000

// Span represents a single tracing span received from the Java runtime.
type Span struct {
	TraceID      string            `json:"trace_id"`
	SpanID       string            `json:"span_id"`
	ParentSpanID string            `json:"parent_span_id,omitempty"`
	Service      string            `json:"service"`
	Operation    string            `json:"operation"`
	Kind         string            `json:"kind"`
	StartTimeMs  int64             `json:"start_time_ms"`
	DurationMs   int64             `json:"duration_ms"`
	Status       string            `json:"status"`
	Attributes   map[string]string `json:"attributes,omitempty"`
}

// Trace represents a complete trace composed of multiple spans.
type Trace struct {
	TraceID       string `json:"trace_id"`
	RootService   string `json:"root_service"`
	RootOperation string `json:"root_operation"`
	StartTimeMs   int64  `json:"start_time_ms"`
	DurationMs    int64  `json:"duration_ms"`
	Status        string `json:"status"`
	SpanCount     int    `json:"span_count"`
	Spans         []Span `json:"spans"`
}

// TraceSummary is a lightweight representation for list views.
type TraceSummary struct {
	TraceID       string `json:"trace_id"`
	RootService   string `json:"root_service"`
	RootOperation string `json:"root_operation"`
	StartTimeMs   int64  `json:"start_time_ms"`
	DurationMs    int64  `json:"duration_ms"`
	Status        string `json:"status"`
	SpanCount     int    `json:"span_count"`
}

// TraceStore is an in-memory ring buffer of traces.
type TraceStore struct {
	mu      sync.RWMutex
	traces  []*Trace          // ordered by insertion (newest last)
	byID    map[string]*Trace // index by trace ID
}

// NewTraceStore creates an empty trace store.
func NewTraceStore() *TraceStore {
	return &TraceStore{
		traces: make([]*Trace, 0, 128),
		byID:   make(map[string]*Trace),
	}
}

// Ingest adds spans to the store, grouping them into traces.
func (ts *TraceStore) Ingest(spans []Span) {
	ts.mu.Lock()
	defer ts.mu.Unlock()

	for _, span := range spans {
		trace, exists := ts.byID[span.TraceID]
		if !exists {
			trace = &Trace{
				TraceID:       span.TraceID,
				RootService:   span.Service,
				RootOperation: span.Operation,
				StartTimeMs:   span.StartTimeMs,
				DurationMs:    span.DurationMs,
				Status:        span.Status,
				Spans:         []Span{},
			}
			ts.byID[span.TraceID] = trace
			ts.traces = append(ts.traces, trace)

			// Evict oldest if over capacity
			if len(ts.traces) > maxTraces {
				old := ts.traces[0]
				delete(ts.byID, old.TraceID)
				ts.traces = ts.traces[1:]
			}
		}

		trace.Spans = append(trace.Spans, span)
		trace.SpanCount = len(trace.Spans)

		// Update root info from the root span (no parent)
		if span.ParentSpanID == "" {
			trace.RootService = span.Service
			trace.RootOperation = span.Operation
			trace.StartTimeMs = span.StartTimeMs
			trace.DurationMs = span.DurationMs
			trace.Status = span.Status
		}

		// Update total duration to cover all spans
		endTime := span.StartTimeMs + span.DurationMs
		traceEnd := trace.StartTimeMs + trace.DurationMs
		if endTime > traceEnd {
			trace.DurationMs = endTime - trace.StartTimeMs
		}

		// Propagate error status
		if span.Status == "ERROR" {
			trace.Status = "ERROR"
		}
	}
}

// List returns the most recent trace summaries.
func (ts *TraceStore) List(limit int) []TraceSummary {
	ts.mu.RLock()
	defer ts.mu.RUnlock()

	if limit <= 0 || limit > len(ts.traces) {
		limit = len(ts.traces)
	}

	result := make([]TraceSummary, limit)
	// Return newest first
	for i := 0; i < limit; i++ {
		t := ts.traces[len(ts.traces)-1-i]
		result[i] = TraceSummary{
			TraceID:       t.TraceID,
			RootService:   t.RootService,
			RootOperation: t.RootOperation,
			StartTimeMs:   t.StartTimeMs,
			DurationMs:    t.DurationMs,
			Status:        t.Status,
			SpanCount:     t.SpanCount,
		}
	}
	return result
}

// Get returns a full trace by ID, or nil if not found.
func (ts *TraceStore) Get(traceID string) *Trace {
	ts.mu.RLock()
	defer ts.mu.RUnlock()
	return ts.byID[traceID]
}

// Clear removes all traces.
func (ts *TraceStore) Clear() {
	ts.mu.Lock()
	defer ts.mu.Unlock()
	ts.traces = make([]*Trace, 0, 128)
	ts.byID = make(map[string]*Trace)
}

// Count returns the number of stored traces.
func (ts *TraceStore) Count() int {
	ts.mu.RLock()
	defer ts.mu.RUnlock()
	return len(ts.traces)
}

package traces

import (
	"testing"
)

func TestIngestAndList(t *testing.T) {
	ts := NewTraceStore()

	spans := []Span{
		{TraceID: "aaa", SpanID: "s1", Service: "user-svc", Operation: "GET /users", Kind: "API", StartTimeMs: 1000, DurationMs: 50, Status: "OK"},
		{TraceID: "aaa", SpanID: "s2", ParentSpanID: "s1", Service: "user-svc", Operation: "db.query users", Kind: "DATABASE", StartTimeMs: 1010, DurationMs: 20, Status: "OK"},
	}
	ts.Ingest(spans)

	if ts.Count() != 1 {
		t.Errorf("Count() = %d, want 1", ts.Count())
	}

	list := ts.List(10)
	if len(list) != 1 {
		t.Fatalf("List() len = %d, want 1", len(list))
	}
	if list[0].TraceID != "aaa" {
		t.Errorf("TraceID = %s, want aaa", list[0].TraceID)
	}
	if list[0].RootOperation != "GET /users" {
		t.Errorf("RootOperation = %s, want GET /users", list[0].RootOperation)
	}
	if list[0].SpanCount != 2 {
		t.Errorf("SpanCount = %d, want 2", list[0].SpanCount)
	}
}

func TestGetTrace(t *testing.T) {
	ts := NewTraceStore()
	ts.Ingest([]Span{
		{TraceID: "bbb", SpanID: "s1", Service: "api", Operation: "POST /orders", Kind: "API", StartTimeMs: 2000, DurationMs: 100, Status: "OK"},
	})

	trace := ts.Get("bbb")
	if trace == nil {
		t.Fatal("Get(bbb) returned nil")
	}
	if len(trace.Spans) != 1 {
		t.Errorf("spans len = %d, want 1", len(trace.Spans))
	}

	if ts.Get("nonexistent") != nil {
		t.Error("Get(nonexistent) should return nil")
	}
}

func TestListNewestFirst(t *testing.T) {
	ts := NewTraceStore()
	ts.Ingest([]Span{
		{TraceID: "t1", SpanID: "s1", Service: "svc", Operation: "op1", Kind: "API", StartTimeMs: 1000, DurationMs: 10, Status: "OK"},
	})
	ts.Ingest([]Span{
		{TraceID: "t2", SpanID: "s2", Service: "svc", Operation: "op2", Kind: "API", StartTimeMs: 2000, DurationMs: 20, Status: "OK"},
	})
	ts.Ingest([]Span{
		{TraceID: "t3", SpanID: "s3", Service: "svc", Operation: "op3", Kind: "API", StartTimeMs: 3000, DurationMs: 30, Status: "OK"},
	})

	list := ts.List(2)
	if len(list) != 2 {
		t.Fatalf("List(2) len = %d, want 2", len(list))
	}
	if list[0].TraceID != "t3" {
		t.Errorf("list[0] = %s, want t3 (newest)", list[0].TraceID)
	}
	if list[1].TraceID != "t2" {
		t.Errorf("list[1] = %s, want t2", list[1].TraceID)
	}
}

func TestErrorPropagation(t *testing.T) {
	ts := NewTraceStore()
	ts.Ingest([]Span{
		{TraceID: "err1", SpanID: "s1", Service: "api", Operation: "GET /fail", Kind: "API", StartTimeMs: 1000, DurationMs: 100, Status: "OK"},
		{TraceID: "err1", SpanID: "s2", ParentSpanID: "s1", Service: "api", Operation: "db.query", Kind: "DATABASE", StartTimeMs: 1020, DurationMs: 30, Status: "ERROR"},
	})

	trace := ts.Get("err1")
	if trace.Status != "ERROR" {
		t.Errorf("trace status = %s, want ERROR (propagated from child span)", trace.Status)
	}
}

func TestDurationCoversAllSpans(t *testing.T) {
	ts := NewTraceStore()
	ts.Ingest([]Span{
		{TraceID: "dur1", SpanID: "s1", Service: "api", Operation: "GET /", Kind: "API", StartTimeMs: 1000, DurationMs: 50},
		{TraceID: "dur1", SpanID: "s2", ParentSpanID: "s1", Service: "api", Operation: "db.query", Kind: "DATABASE", StartTimeMs: 1010, DurationMs: 80},
	})

	trace := ts.Get("dur1")
	// Trace should cover from 1000 to 1090 (1010+80), so duration = 90
	if trace.DurationMs != 90 {
		t.Errorf("DurationMs = %d, want 90", trace.DurationMs)
	}
}

func TestClear(t *testing.T) {
	ts := NewTraceStore()
	ts.Ingest([]Span{
		{TraceID: "c1", SpanID: "s1", Service: "svc", Operation: "op", Kind: "API", StartTimeMs: 1000, DurationMs: 10, Status: "OK"},
	})
	ts.Clear()
	if ts.Count() != 0 {
		t.Errorf("Count after Clear = %d, want 0", ts.Count())
	}
}

func TestEviction(t *testing.T) {
	ts := NewTraceStore()
	// Ingest more than maxTraces
	for i := 0; i < maxTraces+50; i++ {
		ts.Ingest([]Span{
			{TraceID: "t" + string(rune(i)), SpanID: "s1", Service: "svc", Operation: "op", Kind: "API", StartTimeMs: int64(i), DurationMs: 1, Status: "OK"},
		})
	}
	if ts.Count() > maxTraces {
		t.Errorf("Count = %d, should not exceed %d", ts.Count(), maxTraces)
	}
}

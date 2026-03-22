package dev.ko.runtime.tracing;

import java.util.Random;

/**
 * Request-scoped tracing context. Stored in a ThreadLocal to propagate
 * trace/span IDs through the call stack within a single request.
 */
public final class TracingContext {

    private static final ThreadLocal<TracingContext> CURRENT = new ThreadLocal<>();
    private static final Random RANDOM = new Random();

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;

    private TracingContext(String traceId, String spanId, String parentSpanId) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
    }

    /** Start a new trace (root span). */
    public static TracingContext newTrace() {
        TracingContext ctx = new TracingContext(randomHex(32), randomHex(16), null);
        CURRENT.set(ctx);
        return ctx;
    }

    /** Create a child span within the current trace. */
    public static TracingContext childSpan() {
        TracingContext parent = CURRENT.get();
        if (parent == null) {
            return newTrace();
        }
        TracingContext child = new TracingContext(parent.traceId, randomHex(16), parent.spanId);
        CURRENT.set(child);
        return child;
    }

    /** Restore the parent context after a child span completes. */
    public void restore() {
        if (parentSpanId != null) {
            CURRENT.set(new TracingContext(traceId, parentSpanId, null));
        } else {
            CURRENT.remove();
        }
    }

    /** Get the current tracing context, or null if none. */
    public static TracingContext current() {
        return CURRENT.get();
    }

    /** Clear the current context (end of request). */
    public static void clear() {
        CURRENT.remove();
    }

    public String traceId() { return traceId; }
    public String spanId() { return spanId; }
    public String parentSpanId() { return parentSpanId; }

    private static String randomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return sb.toString();
    }
}

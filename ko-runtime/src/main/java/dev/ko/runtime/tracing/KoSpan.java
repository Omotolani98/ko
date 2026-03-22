package dev.ko.runtime.tracing;

import java.util.Map;

/**
 * Represents a single tracing span. Follows OpenTelemetry conventions.
 *
 * @param traceId 32-hex-char trace identifier
 * @param spanId 16-hex-char span identifier
 * @param parentSpanId parent span ID, or null for root spans
 * @param service the Ko service name
 * @param operation e.g. "GET /users/:id", "db.query users", "pubsub.publish user-events"
 * @param kind API, DATABASE, PUBSUB_PUBLISH, PUBSUB_SUBSCRIBE, SERVICE_CALL
 * @param startTimeMs epoch milliseconds
 * @param durationMs span duration in milliseconds
 * @param status OK or ERROR
 * @param attributes additional key-value metadata
 */
public record KoSpan(
        String traceId,
        String spanId,
        String parentSpanId,
        String service,
        String operation,
        String kind,
        long startTimeMs,
        long durationMs,
        String status,
        Map<String, String> attributes
) {}

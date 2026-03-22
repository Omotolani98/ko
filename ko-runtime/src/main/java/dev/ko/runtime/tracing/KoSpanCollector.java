package dev.ko.runtime.tracing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Collects completed spans and ships them to the Ko CLI dashboard
 * via HTTP POST to /api/traces/ingest.
 *
 * Spans are buffered in memory and flushed every 500ms to minimize overhead.
 * If no dashboard is running, spans are silently dropped.
 */
public class KoSpanCollector {

    private static final Logger log = LoggerFactory.getLogger(KoSpanCollector.class);

    private final ConcurrentLinkedQueue<KoSpan> buffer = new ConcurrentLinkedQueue<>();
    private final String ingestUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;

    public KoSpanCollector(int dashboardPort, ObjectMapper objectMapper) {
        this.ingestUrl = "http://localhost:" + dashboardPort + "/api/traces/ingest";
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ko-span-flusher");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::flush, 500, 500, TimeUnit.MILLISECONDS);
    }

    /** Submit a completed span for collection. Thread-safe. */
    public void submit(KoSpan span) {
        buffer.add(span);
    }

    /** Flush buffered spans to the dashboard. */
    private void flush() {
        if (buffer.isEmpty()) return;

        List<KoSpan> batch = new ArrayList<>();
        KoSpan span;
        while ((span = buffer.poll()) != null) {
            batch.add(span);
        }

        try {
            String json = objectMapper.writeValueAsString(Map.of("spans", batch));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ingestUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        log.debug("Ko: Failed to send spans to dashboard: {}", ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.debug("Ko: Failed to serialize spans: {}", e.getMessage());
        }
    }

    /** Shut down the collector. Flushes remaining spans. */
    public void shutdown() {
        scheduler.shutdown();
        flush();
    }
}

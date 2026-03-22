package dev.ko.runtime.pubsub;

import dev.ko.runtime.tracing.KoSpan;
import dev.ko.runtime.tracing.KoSpanCollector;
import dev.ko.runtime.tracing.TracingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Developer-facing pub/sub API. Each @KoPubSub field gets an instance.
 *
 * <pre>
 * {@code
 * @KoPubSub(topic = "user-events")
 * private KoTopic<UserEvent> events;
 *
 * events.publish(new UserEvent("signup", userId));
 * }
 * </pre>
 */
public class KoTopic<T> {

    private static final Logger log = LoggerFactory.getLogger(KoTopic.class);

    private final String name;
    private final KoPubSubProvider provider;
    private volatile KoSpanCollector spanCollector;

    /**
     * Creates a new KoTopic.
     *
     * @param name the topic name
     * @param provider the pub/sub provider
     */
    public KoTopic(String name, KoPubSubProvider provider) {
        this.name = name;
        this.provider = provider;
    }

    /** Set the span collector for tracing. Called by auto-configuration. */
    public void setSpanCollector(KoSpanCollector collector) {
        this.spanCollector = collector;
    }

    /**
     * Returns the topic name.
     *
     * @return the topic name
     */
    public String getName() {
        return name;
    }

    /**
     * Publish a message to this topic. Delivery is synchronous in local dev
     * (in-memory provider) and asynchronous in production providers.
     */
    public void publish(T message) {
        log.debug("Ko: Publishing to topic '{}': {}", name, message);

        TracingContext ctx = null;
        long start = 0;
        if (spanCollector != null && TracingContext.current() != null) {
            ctx = TracingContext.childSpan();
            start = System.currentTimeMillis();
        }

        String status = "OK";
        try {
            provider.publish(name, message);
        } catch (Exception e) {
            status = "ERROR";
            throw e;
        } finally {
            if (ctx != null) {
                long duration = System.currentTimeMillis() - start;
                Map<String, String> attrs = new LinkedHashMap<>();
                attrs.put("pubsub.topic", name);
                attrs.put("pubsub.message_type", message.getClass().getSimpleName());
                spanCollector.submit(new KoSpan(
                        ctx.traceId(), ctx.spanId(), ctx.parentSpanId(),
                        name, "pubsub.publish " + name, "PUBSUB_PUBLISH",
                        start, duration, status, attrs
                ));
                ctx.restore();
            }
        }
    }
}

package dev.ko.runtime.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public KoTopic(String name, KoPubSubProvider provider) {
        this.name = name;
        this.provider = provider;
    }

    public String getName() {
        return name;
    }

    /**
     * Publish a message to this topic. Delivery is synchronous in local dev
     * (in-memory provider) and asynchronous in production providers.
     */
    public void publish(T message) {
        log.debug("Ko: Publishing to topic '{}': {}", name, message);
        provider.publish(name, message);
    }
}

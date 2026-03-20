package dev.ko.runtime.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * In-memory pub/sub provider for local development.
 * Messages are dispatched asynchronously on virtual threads.
 * Each subscription gets its own copy of the message (fan-out).
 */
public class InMemoryPubSubProvider implements KoPubSubProvider {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPubSubProvider.class);

    private final Map<String, List<SubscriptionHandler<?>>> subscriptions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean closed = false;

    @Override
    @SuppressWarnings("unchecked")
    public <T> void publish(String topic, T message) {
        if (closed) {
            throw new IllegalStateException("PubSub provider is closed");
        }

        List<SubscriptionHandler<?>> handlers = subscriptions.get(topic);
        if (handlers == null || handlers.isEmpty()) {
            log.debug("Ko: No subscribers for topic '{}', message dropped", topic);
            return;
        }

        for (SubscriptionHandler<?> handler : handlers) {
            executor.submit(() -> {
                try {
                    ((SubscriptionHandler<T>) handler).handle(message);
                } catch (Exception e) {
                    log.error("Ko: Subscriber '{}' failed on topic '{}': {}",
                            handler.subscription, topic, e.getMessage(), e);
                }
            });
        }

        log.debug("Ko: Published to topic '{}' → {} subscriber(s)", topic, handlers.size());
    }

    @Override
    public <T> void subscribe(String topic, String subscription, Class<T> type, Consumer<T> handler) {
        subscriptions
                .computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                .add(new SubscriptionHandler<>(subscription, type, handler));

        log.info("Ko: Subscribed '{}' to topic '{}'", subscription, topic);
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdown();
        subscriptions.clear();
    }

    private record SubscriptionHandler<T>(
            String subscription,
            Class<T> type,
            Consumer<T> handler
    ) {
        void handle(T message) {
            handler.accept(message);
        }
    }
}

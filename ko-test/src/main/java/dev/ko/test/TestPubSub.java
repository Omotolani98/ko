package dev.ko.test;

import dev.ko.runtime.pubsub.KoPubSubProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Test-aware pub/sub provider that records all published messages
 * for inspection in assertions.
 *
 * <pre>{@code
 * @Autowired TestPubSub testPubSub;
 *
 * @Test
 * void publishesGreetingEvent() {
 *     // ... trigger some action that publishes ...
 *     assertThat(testPubSub.published("greeting-events")).hasSize(1);
 *     assertThat(testPubSub.lastPublished("greeting-events"))
 *         .isInstanceOf(GreetingEvent.class);
 * }
 * }</pre>
 */
public class TestPubSub implements KoPubSubProvider {

    private final Map<String, List<Object>> publishedMessages = new ConcurrentHashMap<>();
    private final Map<String, List<SubscriptionHandler<?>>> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> latches = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> void publish(String topic, T message) {
        publishedMessages
                .computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                .add(message);

        // Deliver to subscribers
        List<SubscriptionHandler<?>> handlers = subscriptions.get(topic);
        if (handlers != null) {
            for (SubscriptionHandler<?> handler : handlers) {
                ((SubscriptionHandler<T>) handler).handle(message);
            }
        }

        // Count down any waiting latch
        CountDownLatch latch = latches.get(topic);
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public <T> void subscribe(String topic, String subscription, Class<T> type, Consumer<T> handler) {
        subscriptions
                .computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                .add(new SubscriptionHandler<>(subscription, type, handler));
    }

    @Override
    public void close() {
        reset();
        subscriptions.clear();
    }

    // --- Test inspection API ---

    /**
     * Returns all messages published to a topic since the last reset.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> published(String topic) {
        List<Object> messages = publishedMessages.get(topic);
        if (messages == null) {
            return Collections.emptyList();
        }
        return (List<T>) Collections.unmodifiableList(new ArrayList<>(messages));
    }

    /**
     * Returns the last message published to a topic, or null if none.
     */
    @SuppressWarnings("unchecked")
    public <T> T lastPublished(String topic) {
        List<Object> messages = publishedMessages.get(topic);
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return (T) messages.get(messages.size() - 1);
    }

    /**
     * Returns the total number of messages published to a topic.
     */
    public int publishedCount(String topic) {
        List<Object> messages = publishedMessages.get(topic);
        return messages == null ? 0 : messages.size();
    }

    /**
     * Wait for at least {@code count} messages to be published to a topic.
     * Returns true if the expected count was reached, false if the timeout elapsed.
     */
    public boolean awaitPublished(String topic, int count, long timeout, TimeUnit unit) throws InterruptedException {
        int current = publishedCount(topic);
        if (current >= count) {
            return true;
        }
        int remaining = count - current;
        CountDownLatch latch = new CountDownLatch(remaining);
        latches.put(topic, latch);
        try {
            return latch.await(timeout, unit);
        } finally {
            latches.remove(topic);
        }
    }

    /**
     * Clear all recorded published messages. Called automatically before each test
     * by {@link KoTestExtension}.
     */
    public void reset() {
        publishedMessages.clear();
        latches.clear();
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

package dev.ko.runtime.pubsub;

import java.util.function.Consumer;

/**
 * Provider interface for pub/sub infrastructure.
 * Implementations handle message delivery for each backend (in-memory, Kafka, SQS, etc.).
 */
public interface KoPubSubProvider {

    /**
     * Publish a message to a topic.
     */
    <T> void publish(String topic, T message);

    /**
     * Subscribe to a topic with a handler.
     *
     * @param topic        topic name
     * @param subscription subscription name (unique per subscriber)
     * @param type         message class for deserialization
     * @param handler      callback invoked for each message
     */
    <T> void subscribe(String topic, String subscription, Class<T> type, Consumer<T> handler);

    /**
     * Shut down all topics and subscriptions.
     */
    void close();
}

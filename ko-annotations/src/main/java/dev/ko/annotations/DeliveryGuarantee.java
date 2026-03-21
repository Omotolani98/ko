package dev.ko.annotations;

/**
 * Delivery guarantee level for pub/sub messaging.
 * Used with {@link KoPubSub#delivery()} to configure topic semantics.
 */
public enum DeliveryGuarantee {
    /** Each message is delivered at least once; duplicates are possible. */
    AT_LEAST_ONCE,
    /** Each message is delivered at most once; messages may be lost. */
    AT_MOST_ONCE,
    /** Each message is delivered exactly once (requires transactional provider support). */
    EXACTLY_ONCE
}

package dev.ko.runtime.cache;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * In-memory cache backed by ConcurrentHashMap with TTL support.
 */
public class KoCacheCluster<K, V> {

    private final String name;
    private final long ttlSeconds;
    private final ConcurrentHashMap<K, CacheEntry<V>> store = new ConcurrentHashMap<>();

    /**
     * Creates a new KoCacheCluster.
     *
     * @param name the cache cluster name
     * @param ttlSeconds the default time-to-live in seconds
     */
    public KoCacheCluster(String name, long ttlSeconds) {
        this.name = name;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Returns the cache cluster name.
     *
     * @return the cache name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the cached value for the given key, if present and not expired.
     *
     * @param key the cache key
     * @return the cached value, or empty if not present or expired
     */
    public Optional<V> get(K key) {
        CacheEntry<V> entry = store.get(key);
        if (entry == null || entry.isExpired()) {
            if (entry != null) {
                store.remove(key);
            }
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    /**
     * Gets the cached value or computes and stores it using the loader.
     *
     * @param key the cache key
     * @param loader supplier to compute the value if absent
     * @return the cached or computed value
     */
    public V getOrSet(K key, Supplier<V> loader) {
        return get(key).orElseGet(() -> {
            V value = loader.get();
            set(key, value);
            return value;
        });
    }

    /**
     * Stores a value with the default TTL.
     *
     * @param key the cache key
     * @param value the value to cache
     */
    public void set(K key, V value) {
        store.put(key, new CacheEntry<>(value, Instant.now().plusSeconds(ttlSeconds)));
    }

    /**
     * Stores a value with a custom TTL.
     *
     * @param key the cache key
     * @param value the value to cache
     * @param ttlSeconds the time-to-live in seconds for this entry
     */
    public void set(K key, V value, long ttlSeconds) {
        store.put(key, new CacheEntry<>(value, Instant.now().plusSeconds(ttlSeconds)));
    }

    /**
     * Removes the entry for the given key.
     *
     * @param key the cache key to remove
     */
    public void delete(K key) {
        store.remove(key);
    }

    private record CacheEntry<V>(V value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}

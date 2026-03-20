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

    public KoCacheCluster(String name, long ttlSeconds) {
        this.name = name;
        this.ttlSeconds = ttlSeconds;
    }

    public String getName() {
        return name;
    }

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

    public V getOrSet(K key, Supplier<V> loader) {
        return get(key).orElseGet(() -> {
            V value = loader.get();
            set(key, value);
            return value;
        });
    }

    public void set(K key, V value) {
        store.put(key, new CacheEntry<>(value, Instant.now().plusSeconds(ttlSeconds)));
    }

    public void set(K key, V value, long ttlSeconds) {
        store.put(key, new CacheEntry<>(value, Instant.now().plusSeconds(ttlSeconds)));
    }

    public void delete(K key) {
        store.remove(key);
    }

    private record CacheEntry<V>(V value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}

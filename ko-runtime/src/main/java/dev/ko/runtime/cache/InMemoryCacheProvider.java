package dev.ko.runtime.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache provider for local development.
 * Each named cache is backed by a {@link KoCacheCluster} using {@link ConcurrentHashMap}.
 */
public class InMemoryCacheProvider implements KoCacheProvider {

    private static final Logger log = LoggerFactory.getLogger(InMemoryCacheProvider.class);

    private final Map<String, KoCacheCluster<?, ?>> caches = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> KoCacheCluster<K, V> getOrCreateCache(String name, long ttlSeconds) {
        return (KoCacheCluster<K, V>) caches.computeIfAbsent(name, n -> {
            log.info("Ko: Created in-memory cache '{}' (ttl={}s)", n, ttlSeconds);
            return new KoCacheCluster<>(n, ttlSeconds);
        });
    }

    @Override
    public void close() {
        caches.clear();
        log.info("Ko: Closed all in-memory caches");
    }
}

package dev.ko.runtime.cache;

/**
 * Provider interface for cache infrastructure.
 * Implementations handle cache storage for each backend (in-memory, Redis, Memcached, etc.).
 */
public interface KoCacheProvider {

    /**
     * Get or create a named cache cluster with the given TTL.
     *
     * @param name       logical cache name (from {@code @KoCache})
     * @param ttlSeconds default TTL in seconds for cache entries
     * @return a cache cluster instance
     */
    <K, V> KoCacheCluster<K, V> getOrCreateCache(String name, long ttlSeconds);

    /**
     * Shut down all caches and release resources.
     */
    void close();
}

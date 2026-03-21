package dev.ko.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a cache cluster resource within a {@link KoService}.
 * Applied to a {@code KoCacheCluster} field.
 *
 * <pre>{@code
 * @KoCache(name = "sessions", keyType = String.class, ttl = 3600)
 * private KoCacheCluster<String, Session> cache;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoCache {
    /**
     * The logical name of the cache cluster.
     *
     * @return the cache name
     */
    String name();

    /**
     * The type used for cache keys.
     *
     * @return the key type class, defaults to {@link String}
     */
    Class<?> keyType() default String.class;

    /**
     * The default time-to-live for cache entries, in seconds.
     *
     * @return the TTL in seconds, defaults to {@code 300}
     */
    long ttl() default 300;
}

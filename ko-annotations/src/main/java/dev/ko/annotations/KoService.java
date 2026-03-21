package dev.ko.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Ko service — the top-level unit of organization
 * in a Ko application. Each service owns its own APIs, databases,
 * pub/sub topics, caches, buckets, secrets, and cron jobs.
 *
 * <pre>{@code
 * @KoService("greeting")
 * public class GreetingService {
 *     // ...
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoService {
    /**
     * The unique name of this service.
     *
     * @return the service name
     */
    String value();
}

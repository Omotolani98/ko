package dev.ko.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a method as an HTTP API endpoint within a {@link KoService}.
 * The annotation processor generates routing metadata and an OpenAPI spec entry.
 *
 * <pre>{@code
 * @KoAPI(method = "POST", path = "/greetings")
 * public Greeting createGreeting(CreateGreetingRequest req) {
 *     // ...
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoAPI {
    /**
     * The HTTP method (GET, POST, PUT, DELETE, PATCH).
     *
     * @return the HTTP method, defaults to {@code "GET"}
     */
    String method() default "GET";

    /**
     * The URL path for this endpoint, relative to the service root.
     * Supports path parameters using {@code :param} syntax (e.g., {@code "/users/:id"}).
     *
     * @return the endpoint path
     */
    String path();

    /**
     * Whether this endpoint requires authentication.
     *
     * @return {@code true} if authentication is required, defaults to {@code false}
     */
    boolean auth() default false;

    /**
     * The permissions required to access this endpoint.
     * Only checked when {@link #auth()} is {@code true}.
     *
     * @return an array of required permission strings
     */
    String[] permissions() default {};

    /**
     * Whether this endpoint is exposed externally.
     * Set to {@code false} for internal service-to-service APIs.
     *
     * @return {@code true} if the endpoint is publicly exposed, defaults to {@code true}
     */
    boolean expose() default true;
}

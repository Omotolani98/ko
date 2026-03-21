package dev.ko.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a method parameter to a path parameter in a {@link KoAPI} endpoint.
 *
 * <pre>{@code
 * @KoAPI(method = "GET", path = "/users/:id")
 * public User getUser(@PathParam("id") String id) {
 *     // ...
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface PathParam {
    /**
     * The name of the path parameter to bind (must match a {@code :param} segment in the path).
     *
     * @return the path parameter name
     */
    String value();
}

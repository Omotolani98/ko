package dev.ko.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a generated service client for making service-to-service calls.
 * Applied to a field whose type is a generated {@code {ServiceName}Client} class.
 *
 * <pre>{@code
 * @KoServiceClient
 * private GreetingServiceClient greetingClient;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoServiceClient {
    /**
     * The target service name. If empty, inferred from the field type.
     *
     * @return the service name, or empty to auto-detect
     */
    String value() default "";
}

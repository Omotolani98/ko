package dev.ko.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a secret resource within a {@link KoService}.
 * Applied to a {@code KoSecretValue} field.
 * The secret is resolved at runtime from the configured secret provider.
 *
 * <pre>{@code
 * @KoSecret("stripe-api-key")
 * private KoSecretValue stripeKey;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoSecret {
    /**
     * The name of the secret.
     *
     * @return the secret name
     */
    String value();
}

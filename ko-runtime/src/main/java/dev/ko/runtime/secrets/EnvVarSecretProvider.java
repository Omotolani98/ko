package dev.ko.runtime.secrets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves secrets from environment variables.
 * Secret name "stripe-api-key" maps to env var "STRIPE_API_KEY".
 */
public class EnvVarSecretProvider implements KoSecretProvider {

    private static final Logger log = LoggerFactory.getLogger(EnvVarSecretProvider.class);

    @Override
    public String resolve(String name) {
        String envVar = toEnvVar(name);
        String value = System.getenv(envVar);
        if (value == null) {
            log.warn("Ko: Secret '{}' not found (looked for env var '{}')", name, envVar);
        }
        return value;
    }

    /**
     * Converts kebab-case secret name to UPPER_SNAKE_CASE env var name.
     * e.g. "stripe-api-key" → "STRIPE_API_KEY"
     */
    static String toEnvVar(String name) {
        return name.replace('-', '_').toUpperCase();
    }
}

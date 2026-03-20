package dev.ko.runtime.secrets;

/**
 * Provider interface for secret resolution.
 * Local: environment variables. Production: AWS Secrets Manager, GCP Secret Manager, Vault, etc.
 */
public interface KoSecretProvider {

    /**
     * Resolve a secret by name.
     *
     * @param name the secret name (kebab-case, e.g. "stripe-api-key")
     * @return the secret value, or null if not found
     */
    String resolve(String name);
}

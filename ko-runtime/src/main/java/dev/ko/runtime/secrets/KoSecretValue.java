package dev.ko.runtime.secrets;

/**
 * Developer-facing API for accessing a resolved secret.
 * Injected into @KoService fields annotated with @KoSecret.
 */
public final class KoSecretValue {

    private final String name;
    private final KoSecretProvider provider;

    /**
     * Creates a new KoSecretValue.
     *
     * @param name the secret name
     * @param provider the secret provider used for resolution
     */
    public KoSecretValue(String name, KoSecretProvider provider) {
        this.name = name;
        this.provider = provider;
    }

    /** Return the secret name. */
    public String name() {
        return name;
    }

    /** Resolve and return the secret value. Returns null if not set. */
    public String value() {
        return provider.resolve(name);
    }

    /** Resolve the secret value, throwing if not set. */
    public String requireValue() {
        String v = provider.resolve(name);
        if (v == null) {
            throw new IllegalStateException("Required secret '" + name + "' is not set");
        }
        return v;
    }

    @Override
    public String toString() {
        return "KoSecretValue[" + name + "]";
    }
}

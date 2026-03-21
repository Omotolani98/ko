package dev.ko.test;

import dev.ko.runtime.secrets.KoSecretProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory secret provider for tests.
 * Allows setting secret values without environment variables.
 *
 * <pre>{@code
 * @Autowired TestSecretProvider testSecrets;
 *
 * @BeforeEach
 * void setUp() {
 *     testSecrets.set("api-key", "test-key-123");
 * }
 * }</pre>
 */
public class TestSecretProvider implements KoSecretProvider {

    private final Map<String, String> secrets = new ConcurrentHashMap<>();

    /**
     * Set a secret value for tests.
     */
    public void set(String name, String value) {
        secrets.put(name, value);
    }

    /**
     * Remove a secret.
     */
    public void remove(String name) {
        secrets.remove(name);
    }

    /**
     * Clear all secrets.
     */
    public void reset() {
        secrets.clear();
    }

    /** {@inheritDoc} */
    @Override
    public String resolve(String name) {
        return secrets.get(name);
    }
}

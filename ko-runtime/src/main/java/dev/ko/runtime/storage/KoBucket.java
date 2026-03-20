package dev.ko.runtime.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Developer-facing object storage API. Each @KoBucket field gets an instance.
 *
 * <pre>
 * {@code
 * @KoBucket(name = "avatars", publicRead = true)
 * private KoBucket avatars;
 *
 * avatars.upload("user-123.png", imageBytes, "image/png");
 * byte[] data = avatars.download("user-123.png");
 * }
 * </pre>
 */
public class KoBucket {

    private static final Logger log = LoggerFactory.getLogger(KoBucket.class);

    private final String name;
    private final KoStorageProvider provider;

    public KoBucket(String name, KoStorageProvider provider) {
        this.name = name;
        this.provider = provider;
    }

    public String getName() {
        return name;
    }

    /**
     * Upload an object to this bucket.
     */
    public void upload(String key, byte[] data, String contentType) {
        log.debug("Ko: Uploading '{}' to bucket '{}'", key, name);
        provider.upload(name, key, data, contentType);
    }

    /**
     * Download an object from this bucket.
     * Returns null if the key does not exist.
     */
    public byte[] download(String key) {
        return provider.download(name, key);
    }

    /**
     * Delete an object from this bucket.
     */
    public void delete(String key) {
        log.debug("Ko: Deleting '{}' from bucket '{}'", key, name);
        provider.delete(name, key);
    }

    /**
     * List object keys in this bucket with an optional prefix.
     */
    public List<String> list(String prefix) {
        return provider.list(name, prefix);
    }

    /**
     * List all object keys in this bucket.
     */
    public List<String> list() {
        return list("");
    }

    /**
     * Check if an object exists in this bucket.
     */
    public boolean exists(String key) {
        return provider.exists(name, key);
    }
}

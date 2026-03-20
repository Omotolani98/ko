package dev.ko.runtime.storage;

import java.io.InputStream;
import java.util.List;

/**
 * Provider interface for object storage infrastructure.
 * Implementations handle file storage for each backend (local filesystem, S3, GCS, etc.).
 */
public interface KoStorageProvider {

    /**
     * Upload an object to a bucket.
     */
    void upload(String bucket, String key, byte[] data, String contentType);

    /**
     * Download an object from a bucket.
     * Returns null if the key does not exist.
     */
    byte[] download(String bucket, String key);

    /**
     * Delete an object from a bucket.
     */
    void delete(String bucket, String key);

    /**
     * List object keys in a bucket with an optional prefix.
     */
    List<String> list(String bucket, String prefix);

    /**
     * Check if an object exists.
     */
    boolean exists(String bucket, String key);
}

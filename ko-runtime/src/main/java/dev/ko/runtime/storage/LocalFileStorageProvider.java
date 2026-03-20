package dev.ko.runtime.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Local filesystem storage provider for development.
 * Each bucket maps to a directory under the configured base path.
 */
public class LocalFileStorageProvider implements KoStorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageProvider.class);

    private final Path basePath;

    public LocalFileStorageProvider(Path basePath) {
        this.basePath = basePath;
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create storage base directory: " + basePath, e);
        }
        log.info("Ko: Local file storage at {}", basePath.toAbsolutePath());
    }

    @Override
    public void upload(String bucket, String key, byte[] data, String contentType) {
        try {
            Path file = resolve(bucket, key);
            Files.createDirectories(file.getParent());
            Files.write(file, data);
            log.debug("Ko: Stored '{}/{}' ({} bytes)", bucket, key, data.length);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload " + bucket + "/" + key, e);
        }
    }

    @Override
    public byte[] download(String bucket, String key) {
        try {
            Path file = resolve(bucket, key);
            if (!Files.exists(file)) return null;
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to download " + bucket + "/" + key, e);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            Path file = resolve(bucket, key);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + bucket + "/" + key, e);
        }
    }

    @Override
    public List<String> list(String bucket, String prefix) {
        try {
            Path bucketDir = basePath.resolve(bucket);
            if (!Files.exists(bucketDir)) return List.of();

            List<String> keys = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(bucketDir)) {
                stream.filter(Files::isRegularFile)
                        .map(p -> bucketDir.relativize(p).toString().replace('\\', '/'))
                        .filter(k -> prefix == null || prefix.isEmpty() || k.startsWith(prefix))
                        .forEach(keys::add);
            }
            return keys;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + bucket + "/" + prefix, e);
        }
    }

    @Override
    public boolean exists(String bucket, String key) {
        return Files.exists(resolve(bucket, key));
    }

    private Path resolve(String bucket, String key) {
        return basePath.resolve(bucket).resolve(key);
    }
}

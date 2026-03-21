package dev.ko.runtime.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Java mapping of {@code infra-config.json}.
 * Describes how logical infrastructure maps to physical backends per environment.
 *
 * <p>The CLI generates this for local dev (Testcontainer ports).
 * For production, it is baked into the Docker image or mounted as a file.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InfraConfig(
        Metadata metadata,
        List<SqlServer> sqlServers,
        List<PubSubConfig> pubsub,
        Map<String, RedisConfig> redis,
        List<ObjectStorageConfig> objectStorage,
        Map<String, ServiceDiscoveryEntry> serviceDiscovery,
        Map<String, Object> secrets,
        GracefulShutdown gracefulShutdown
) {

    public InfraConfig {
        if (sqlServers == null) sqlServers = List.of();
        if (pubsub == null) pubsub = List.of();
        if (redis == null) redis = Map.of();
        if (objectStorage == null) objectStorage = List.of();
        if (serviceDiscovery == null) serviceDiscovery = Map.of();
        if (secrets == null) secrets = Map.of();
    }

    /**
     * Returns a default config for local development (no infra-config.json present).
     */
    public static InfraConfig local() {
        return new InfraConfig(
                new Metadata(null, "local", "development", "local", null),
                null, null, null, null, null, null, null);
    }

    /**
     * Convenience: is this a local/development environment?
     */
    public boolean isLocal() {
        return metadata != null && ("development".equals(metadata.envType()) || "test".equals(metadata.envType()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            String appId,
            String envName,
            String envType,
            String cloud,
            String baseUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SqlServer(
            String host,
            TlsConfig tlsConfig,
            Map<String, DatabaseConfig> databases
    ) {
        public SqlServer {
            if (databases == null) databases = Map.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TlsConfig(
            boolean disabled,
            String ca
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DatabaseConfig(
            String username,
            Object password,
            int maxConnections,
            int minConnections
    ) {
        public DatabaseConfig {
            if (maxConnections <= 0) maxConnections = 20;
            if (minConnections <= 0) minConnections = 5;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PubSubConfig(
            String type,
            String bootstrapServers,
            Map<String, TopicConfig> topics
    ) {
        public PubSubConfig {
            if (topics == null) topics = Map.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopicConfig(
            String name,
            int partitions,
            Map<String, SubscriptionConfig> subscriptions
    ) {
        public TopicConfig {
            if (subscriptions == null) subscriptions = Map.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubscriptionConfig(
            String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RedisConfig(
            String host,
            int databaseIndex,
            RedisAuth auth
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RedisAuth(
            String type,
            String username,
            Object password
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObjectStorageConfig(
            String type,
            String region,
            Map<String, BucketConfig> buckets
    ) {
        public ObjectStorageConfig {
            if (buckets == null) buckets = Map.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BucketConfig(
            String name,
            String keyPrefix
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServiceDiscoveryEntry(
            String baseUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GracefulShutdown(
            int total,
            int handlers
    ) {
        public GracefulShutdown {
            if (total <= 0) total = 30;
            if (handlers <= 0) handlers = 20;
        }
    }
}

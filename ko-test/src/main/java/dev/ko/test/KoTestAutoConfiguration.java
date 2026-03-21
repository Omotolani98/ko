package dev.ko.test;

import dev.ko.runtime.cache.InMemoryCacheProvider;
import dev.ko.runtime.cache.KoCacheProvider;
import dev.ko.runtime.database.KoDatabaseProvider;
import dev.ko.runtime.database.KoSQLDatabase;
import dev.ko.runtime.database.LocalDatabaseProvider;
import dev.ko.runtime.model.AppModel;
import dev.ko.runtime.model.DatabaseModel;
import dev.ko.runtime.model.ServiceModel;
import dev.ko.runtime.pubsub.KoPubSubProvider;
import dev.ko.runtime.secrets.KoSecretProvider;
import dev.ko.runtime.service.KoServiceCaller;
import dev.ko.runtime.storage.KoStorageProvider;
import dev.ko.runtime.storage.LocalFileStorageProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for Ko test utilities.
 * Provides test-specific implementations of Ko providers that support
 * inspection and manipulation in tests.
 *
 * <p>Uses {@link Primary} to override the default runtime providers
 * when this module is on the classpath.</p>
 */
@AutoConfiguration
@ConditionalOnResource(resources = "classpath:ko-app-model.json")
public class KoTestAutoConfiguration {

    /**
     * Creates a {@link TestPubSub} as the primary pub/sub provider.
     *
     * @return the test pub/sub provider
     */
    @Bean
    @Primary
    public KoPubSubProvider testPubSubProvider() {
        return new TestPubSub();
    }

    /**
     * Exposes the test pub/sub provider as a {@link TestPubSub} bean for direct inspection.
     *
     * @param pubSubProvider the primary pub/sub provider
     * @return the test pub/sub instance
     */
    @Bean
    public TestPubSub testPubSub(KoPubSubProvider pubSubProvider) {
        return (TestPubSub) pubSubProvider;
    }

    /**
     * Creates a {@link TestSecretProvider} as the primary secret provider.
     *
     * @return the test secret provider
     */
    @Bean
    @Primary
    public KoSecretProvider testSecretProvider() {
        return new TestSecretProvider();
    }

    /**
     * Exposes the test secret provider as a {@link TestSecretProvider} bean for direct manipulation.
     *
     * @param secretProvider the primary secret provider
     * @return the test secret provider instance
     */
    @Bean
    public TestSecretProvider testSecrets(KoSecretProvider secretProvider) {
        return (TestSecretProvider) secretProvider;
    }

    /**
     * Creates a {@link MockServiceClient} as the primary service caller.
     *
     * @return the mock service caller
     */
    @Bean
    @Primary
    public KoServiceCaller mockServiceCaller() {
        return new MockServiceClient();
    }

    /**
     * Exposes the mock service caller as a {@link MockServiceClient} bean for stubbing and inspection.
     *
     * @param serviceCaller the primary service caller
     * @return the mock service client instance
     */
    @Bean
    public MockServiceClient mockServiceClient(KoServiceCaller serviceCaller) {
        return (MockServiceClient) serviceCaller;
    }

    /**
     * Creates a {@link LocalDatabaseProvider} as the primary database provider for tests.
     *
     * @return the test database provider
     */
    @Bean
    @Primary
    public KoDatabaseProvider testDatabaseProvider() {
        return new LocalDatabaseProvider();
    }

    /**
     * Creates an {@link InMemoryCacheProvider} as the primary cache provider for tests.
     *
     * @return the test cache provider
     */
    @Bean
    @Primary
    public KoCacheProvider testCacheProvider() {
        return new InMemoryCacheProvider();
    }

    /**
     * Creates a {@link LocalFileStorageProvider} as the primary storage provider for tests.
     *
     * @return the test storage provider
     */
    @Bean
    @Primary
    public KoStorageProvider testStorageProvider() {
        Path storagePath = Path.of(System.getProperty("java.io.tmpdir"), "ko-test-storage");
        return new LocalFileStorageProvider(storagePath);
    }

    /**
     * Creates a {@link TestDatabase} bean from all databases declared in the app model.
     *
     * @param appModel the application model
     * @param databaseProvider the database provider
     * @return the test database helper
     */
    @Bean
    public TestDatabase testDatabase(AppModel appModel, KoDatabaseProvider databaseProvider) {
        Map<String, KoSQLDatabase> databases = new HashMap<>();
        for (ServiceModel service : appModel.services()) {
            for (DatabaseModel db : service.databases()) {
                if (!databases.containsKey(db.name())) {
                    DataSource ds = databaseProvider.getDataSource(db.name());
                    databases.put(db.name(), new KoSQLDatabase(db.name(), ds));
                }
            }
        }
        return new TestDatabase(databases);
    }
}

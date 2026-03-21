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

    @Bean
    @Primary
    public KoPubSubProvider testPubSubProvider() {
        return new TestPubSub();
    }

    @Bean
    public TestPubSub testPubSub(KoPubSubProvider pubSubProvider) {
        return (TestPubSub) pubSubProvider;
    }

    @Bean
    @Primary
    public KoSecretProvider testSecretProvider() {
        return new TestSecretProvider();
    }

    @Bean
    public TestSecretProvider testSecrets(KoSecretProvider secretProvider) {
        return (TestSecretProvider) secretProvider;
    }

    @Bean
    @Primary
    public KoServiceCaller mockServiceCaller() {
        return new MockServiceClient();
    }

    @Bean
    public MockServiceClient mockServiceClient(KoServiceCaller serviceCaller) {
        return (MockServiceClient) serviceCaller;
    }

    @Bean
    @Primary
    public KoDatabaseProvider testDatabaseProvider() {
        return new LocalDatabaseProvider();
    }

    @Bean
    @Primary
    public KoCacheProvider testCacheProvider() {
        return new InMemoryCacheProvider();
    }

    @Bean
    @Primary
    public KoStorageProvider testStorageProvider() {
        Path storagePath = Path.of(System.getProperty("java.io.tmpdir"), "ko-test-storage");
        return new LocalFileStorageProvider(storagePath);
    }

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

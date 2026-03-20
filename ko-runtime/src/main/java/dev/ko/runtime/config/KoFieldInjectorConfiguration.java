package dev.ko.runtime.config;

import dev.ko.runtime.cache.KoCacheCluster;
import dev.ko.runtime.database.KoDatabaseProvider;
import dev.ko.runtime.database.KoMigrationRunner;
import dev.ko.runtime.database.KoSQLDatabase;
import dev.ko.runtime.model.AppModel;
import dev.ko.runtime.model.BucketModel;
import dev.ko.runtime.model.CacheModel;
import dev.ko.runtime.model.DatabaseModel;
import dev.ko.runtime.model.PubSubTopicModel;
import dev.ko.runtime.model.ServiceModel;
import dev.ko.runtime.pubsub.KoPubSubProvider;
import dev.ko.runtime.pubsub.KoTopic;
import dev.ko.runtime.secrets.KoSecretProvider;
import dev.ko.runtime.service.InProcessCaller;
import dev.ko.runtime.service.KoFieldInjector;
import dev.ko.runtime.service.KoServiceCaller;
import dev.ko.runtime.storage.KoBucketStore;
import dev.ko.runtime.storage.KoStorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Separate config for KoFieldInjector (a BeanPostProcessor) and KoServiceCaller.
 * BeanPostProcessors must live in their own config class to avoid disrupting
 * bean registration in the main config.
 */
@AutoConfiguration(after = KoAutoConfiguration.class)
@ConditionalOnResource(resources = "classpath:ko-app-model.json")
public class KoFieldInjectorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KoFieldInjectorConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(KoServiceCaller.class)
    public KoServiceCaller koServiceCaller(ApplicationContext context, AppModel appModel) {
        return new InProcessCaller(context, appModel);
    }

    @Bean
    public static KoFieldInjector koFieldInjector(AppModel appModel,
                                                   KoDatabaseProvider databaseProvider,
                                                   KoPubSubProvider pubSubProvider,
                                                   KoStorageProvider storageProvider,
                                                   KoSecretProvider secretProvider,
                                                   ApplicationContext applicationContext) {
        Map<String, KoSQLDatabase> databases = new HashMap<>();
        Map<String, KoCacheCluster<?, ?>> caches = new HashMap<>();
        Map<String, KoTopic<?>> topics = new HashMap<>();
        Map<String, KoBucketStore> buckets = new HashMap<>();

        for (ServiceModel service : appModel.services()) {
            for (DatabaseModel db : service.databases()) {
                if (!databases.containsKey(db.name())) {
                    DataSource ds = databaseProvider.getDataSource(db.name());
                    KoMigrationRunner.run(ds, db.name(), db.migrations());
                    databases.put(db.name(), new KoSQLDatabase(db.name(), ds));
                }
            }
            for (CacheModel cache : service.caches()) {
                caches.put(cache.name(), new KoCacheCluster<>(cache.name(), cache.ttl()));
            }
            if (service.buckets() != null) {
                for (BucketModel bucket : service.buckets()) {
                    if (!buckets.containsKey(bucket.name())) {
                        buckets.put(bucket.name(), new KoBucketStore(bucket.name(), storageProvider));
                        log.info("Ko: Created bucket '{}'", bucket.name());
                    }
                }
            }
        }

        if (appModel.pubsubTopics() != null) {
            for (PubSubTopicModel topic : appModel.pubsubTopics()) {
                topics.put(topic.name(), new KoTopic<>(topic.name(), pubSubProvider));
                log.info("Ko: Created topic '{}'", topic.name());
            }
        }

        return new KoFieldInjector(databases, caches, topics, buckets, secretProvider, applicationContext);
    }
}

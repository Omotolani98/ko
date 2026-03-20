package dev.ko.runtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ko.runtime.api.KoEndpointRegistrar;
import dev.ko.runtime.api.KoPathParamResolver;
import dev.ko.runtime.api.KoRequestBodyResolver;
import dev.ko.runtime.cache.KoCache;
import dev.ko.runtime.cron.KoCronScheduler;
import dev.ko.runtime.database.KoDatabaseProvider;
import dev.ko.runtime.database.KoMigrationRunner;
import dev.ko.runtime.database.KoSQLDatabase;
import dev.ko.runtime.database.LocalDatabaseProvider;
import dev.ko.runtime.model.AppModel;
import dev.ko.runtime.model.BucketModel;
import dev.ko.runtime.model.CacheModel;
import dev.ko.runtime.model.DatabaseModel;
import dev.ko.runtime.model.PubSubTopicModel;
import dev.ko.runtime.model.ServiceModel;
import dev.ko.runtime.storage.KoBucket;
import dev.ko.runtime.storage.KoStorageProvider;
import dev.ko.runtime.storage.LocalFileStorageProvider;
import dev.ko.runtime.pubsub.InMemoryPubSubProvider;
import dev.ko.runtime.pubsub.KoPubSubProvider;
import dev.ko.runtime.pubsub.KoSubscriberRegistrar;
import dev.ko.runtime.pubsub.KoTopic;
import dev.ko.runtime.service.KoFieldInjector;
import dev.ko.runtime.service.KoServiceRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AutoConfiguration
@ConditionalOnResource(resources = "classpath:ko-app-model.json")
public class KoAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KoAutoConfiguration.class);

    @Bean
    public AppModel koAppModel() {
        AppModel model = AppModelLoader.load();
        log.info("Ko: Loaded app model '{}' with {} service(s)",
                model.app(), model.services().size());
        return model;
    }

    @Bean
    public static KoServiceRegistrar koServiceRegistrar() {
        return new KoServiceRegistrar();
    }

    @Bean
    @ConditionalOnMissingBean(KoDatabaseProvider.class)
    public KoDatabaseProvider koDatabaseProvider() {
        return new LocalDatabaseProvider();
    }

    @Bean
    @ConditionalOnMissingBean(KoPubSubProvider.class)
    public KoPubSubProvider koPubSubProvider() {
        return new InMemoryPubSubProvider();
    }

    @Bean
    @ConditionalOnMissingBean(KoStorageProvider.class)
    public KoStorageProvider koStorageProvider() {
        Path storagePath = Path.of(System.getProperty("java.io.tmpdir"), "ko-storage");
        return new LocalFileStorageProvider(storagePath);
    }

    @Bean
    public KoFieldInjector koFieldInjector(AppModel appModel,
                                           KoDatabaseProvider databaseProvider,
                                           KoPubSubProvider pubSubProvider,
                                           KoStorageProvider storageProvider) {
        Map<String, KoSQLDatabase> databases = new HashMap<>();
        Map<String, KoCache<?, ?>> caches = new HashMap<>();
        Map<String, KoTopic<?>> topics = new HashMap<>();
        Map<String, KoBucket> buckets = new HashMap<>();

        for (ServiceModel service : appModel.services()) {
            for (DatabaseModel db : service.databases()) {
                if (!databases.containsKey(db.name())) {
                    DataSource ds = databaseProvider.getDataSource(db.name());
                    KoMigrationRunner.run(ds, db.name(), db.migrations());
                    databases.put(db.name(), new KoSQLDatabase(db.name(), ds));
                }
            }
            for (CacheModel cache : service.caches()) {
                caches.put(cache.name(), new KoCache<>(cache.name(), cache.ttl()));
            }
            if (service.buckets() != null) {
                for (BucketModel bucket : service.buckets()) {
                    if (!buckets.containsKey(bucket.name())) {
                        buckets.put(bucket.name(), new KoBucket(bucket.name(), storageProvider));
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

        return new KoFieldInjector(databases, caches, topics, buckets);
    }

    @Bean
    public KoSubscriberRegistrar koSubscriberRegistrar(ApplicationContext context,
                                                       KoPubSubProvider pubSubProvider) {
        return new KoSubscriberRegistrar(context, pubSubProvider);
    }

    @Bean
    public KoCronScheduler koCronScheduler(ApplicationContext context, AppModel appModel) {
        return new KoCronScheduler(context, appModel);
    }

    @Bean
    public KoEndpointRegistrar koEndpointRegistrar(
            RequestMappingHandlerMapping handlerMapping,
            ApplicationContext context,
            AppModel appModel,
            ObjectMapper objectMapper) {
        return new KoEndpointRegistrar(handlerMapping, context, appModel, objectMapper);
    }

    @Bean
    public WebMvcConfigurer koWebMvcConfigurer(ObjectMapper objectMapper) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new KoPathParamResolver());
                resolvers.add(new KoRequestBodyResolver(objectMapper));
            }
        };
    }
}

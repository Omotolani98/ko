package dev.ko.runtime.service;

import dev.ko.annotations.KoBucket;
import dev.ko.annotations.KoCache;
import dev.ko.annotations.KoDatabase;
import dev.ko.annotations.KoPubSub;
import dev.ko.annotations.KoSecret;
import dev.ko.annotations.KoService;
import dev.ko.annotations.KoServiceClient;
import dev.ko.runtime.cache.KoCacheCluster;
import dev.ko.runtime.database.KoSQLDatabase;
import dev.ko.runtime.pubsub.KoTopic;
import dev.ko.runtime.secrets.KoSecretProvider;
import dev.ko.runtime.secrets.KoSecretValue;
import dev.ko.runtime.storage.KoBucketStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Injects Ko infrastructure instances into @KoService bean fields
 * annotated with @KoDatabase, @KoCache, @KoPubSub, @KoBucket, etc.
 */
public class KoFieldInjector implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(KoFieldInjector.class);

    private final Map<String, KoSQLDatabase> databases;
    private final Map<String, KoCacheCluster<?, ?>> caches;
    private final Map<String, KoTopic<?>> topics;
    private final Map<String, KoBucketStore> buckets;
    private final KoSecretProvider secretProvider;
    private final KoServiceCaller serviceCaller;

    public KoFieldInjector(
            Map<String, KoSQLDatabase> databases,
            Map<String, KoCacheCluster<?, ?>> caches,
            Map<String, KoTopic<?>> topics,
            Map<String, KoBucketStore> buckets,
            KoSecretProvider secretProvider,
            KoServiceCaller serviceCaller) {
        this.databases = databases;
        this.caches = caches;
        this.topics = topics;
        this.buckets = buckets;
        this.secretProvider = secretProvider;
        this.serviceCaller = serviceCaller;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!bean.getClass().isAnnotationPresent(KoService.class)) {
            return bean;
        }

        for (Field field : bean.getClass().getDeclaredFields()) {
            try {
                KoDatabase dbAnnotation = field.getAnnotation(KoDatabase.class);
                if (dbAnnotation != null) {
                    field.setAccessible(true);
                    field.set(bean, databases.get(dbAnnotation.name()));
                }

                KoCache cacheAnnotation = field.getAnnotation(KoCache.class);
                if (cacheAnnotation != null) {
                    field.setAccessible(true);
                    field.set(bean, caches.get(cacheAnnotation.name()));
                }

                KoPubSub pubsubAnnotation = field.getAnnotation(KoPubSub.class);
                if (pubsubAnnotation != null) {
                    field.setAccessible(true);
                    field.set(bean, topics.get(pubsubAnnotation.topic()));
                }

                KoBucket bucketAnnotation = field.getAnnotation(KoBucket.class);
                if (bucketAnnotation != null) {
                    field.setAccessible(true);
                    field.set(bean, buckets.get(bucketAnnotation.name()));
                }

                KoSecret secretAnnotation = field.getAnnotation(KoSecret.class);
                if (secretAnnotation != null) {
                    field.setAccessible(true);
                    field.set(bean, new KoSecretValue(secretAnnotation.value(), secretProvider));
                }

                KoServiceClient clientAnnotation = field.getAnnotation(KoServiceClient.class);
                if (clientAnnotation != null) {
                    field.setAccessible(true);
                    Object client = field.getType().getDeclaredConstructor().newInstance();
                    // Inject the KoServiceCaller via setCaller method
                    Method setCaller = field.getType().getMethod("setCaller", KoServiceCaller.class);
                    setCaller.invoke(client, serviceCaller);
                    field.set(bean, client);
                    log.info("Ko: Injected service client '{}'", field.getType().getSimpleName());
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to inject field " + field.getName()
                        + " on " + bean.getClass().getName(), e);
            } catch (Exception e) {
                throw new RuntimeException("Failed to inject field " + field.getName()
                        + " on " + bean.getClass().getName(), e);
            }
        }

        return bean;
    }
}

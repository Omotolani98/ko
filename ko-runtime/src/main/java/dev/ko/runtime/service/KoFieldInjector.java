package dev.ko.runtime.service;

import dev.ko.annotations.KoCache;
import dev.ko.annotations.KoDatabase;
import dev.ko.annotations.KoService;
import dev.ko.runtime.database.KoSQLDatabase;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Injects Ko infrastructure instances into @KoService bean fields
 * annotated with @KoDatabase, @KoCache, etc.
 */
public class KoFieldInjector implements BeanPostProcessor {

    private final Map<String, KoSQLDatabase> databases;
    private final Map<String, dev.ko.runtime.cache.KoCache<?, ?>> caches;

    public KoFieldInjector(
            Map<String, KoSQLDatabase> databases,
            Map<String, dev.ko.runtime.cache.KoCache<?, ?>> caches) {
        this.databases = databases;
        this.caches = caches;
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
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to inject field " + field.getName()
                        + " on " + bean.getClass().getName(), e);
            }
        }

        return bean;
    }
}

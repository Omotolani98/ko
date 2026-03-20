package dev.ko.runtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ko.runtime.api.KoEndpointRegistrar;
import dev.ko.runtime.api.KoPathParamResolver;
import dev.ko.runtime.api.KoRequestBodyResolver;
import dev.ko.runtime.cache.KoCache;
import dev.ko.runtime.database.KoSQLDatabase;
import dev.ko.runtime.model.AppModel;
import dev.ko.runtime.model.CacheModel;
import dev.ko.runtime.model.DatabaseModel;
import dev.ko.runtime.model.ServiceModel;
import dev.ko.runtime.service.KoFieldInjector;
import dev.ko.runtime.service.KoServiceRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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
    public KoFieldInjector koFieldInjector(AppModel appModel) {
        Map<String, KoSQLDatabase> databases = new HashMap<>();
        Map<String, KoCache<?, ?>> caches = new HashMap<>();

        for (ServiceModel service : appModel.services()) {
            for (DatabaseModel db : service.databases()) {
                databases.put(db.name(), new KoSQLDatabase(db.name()));
            }
            for (CacheModel cache : service.caches()) {
                caches.put(cache.name(), new KoCache<>(cache.name(), cache.ttl()));
            }
        }

        return new KoFieldInjector(databases, caches);
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

package dev.ko.runtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ko.runtime.api.KoEndpointRegistrar;
import dev.ko.runtime.api.KoPathParamResolver;
import dev.ko.runtime.api.KoRequestBodyResolver;
import dev.ko.runtime.cron.KoCronScheduler;
import dev.ko.runtime.database.KoDatabaseProvider;
import dev.ko.runtime.database.LocalDatabaseProvider;
import dev.ko.runtime.errors.KoExceptionHandler;
import dev.ko.runtime.model.AppModel;
import dev.ko.runtime.cache.InMemoryCacheProvider;
import dev.ko.runtime.cache.KoCacheProvider;
import dev.ko.runtime.storage.KoStorageProvider;
import dev.ko.runtime.storage.LocalFileStorageProvider;
import dev.ko.runtime.pubsub.InMemoryPubSubProvider;
import dev.ko.runtime.pubsub.KoPubSubProvider;
import dev.ko.runtime.pubsub.KoSubscriberRegistrar;
import dev.ko.runtime.secrets.EnvVarSecretProvider;
import dev.ko.runtime.secrets.KoSecretProvider;
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

import java.nio.file.Path;
import java.util.List;

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
    @ConditionalOnMissingBean(InfraConfig.class)
    public InfraConfig koInfraConfig() {
        return InfraConfigLoader.load();
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
    @ConditionalOnMissingBean(KoCacheProvider.class)
    public KoCacheProvider koCacheProvider() {
        return new InMemoryCacheProvider();
    }

    @Bean
    @ConditionalOnMissingBean(KoStorageProvider.class)
    public KoStorageProvider koStorageProvider() {
        Path storagePath = Path.of(System.getProperty("java.io.tmpdir"), "ko-storage");
        return new LocalFileStorageProvider(storagePath);
    }

    @Bean
    @ConditionalOnMissingBean(KoSecretProvider.class)
    public KoSecretProvider koSecretProvider() {
        return new EnvVarSecretProvider();
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
    @ConditionalOnMissingBean(KoExceptionHandler.class)
    public KoExceptionHandler koExceptionHandler() {
        return new KoExceptionHandler();
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

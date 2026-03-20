package dev.ko.runtime.pubsub;

import dev.ko.annotations.KoService;
import dev.ko.annotations.KoSubscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;

/**
 * Discovers @KoSubscribe methods on @KoService beans and registers them
 * with the PubSub provider at startup.
 */
public class KoSubscriberRegistrar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(KoSubscriberRegistrar.class);

    private final ApplicationContext context;
    private final KoPubSubProvider provider;

    public KoSubscriberRegistrar(ApplicationContext context, KoPubSubProvider provider) {
        this.context = context;
        this.provider = provider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        String[] beanNames = context.getBeanNamesForAnnotation(KoService.class);

        for (String beanName : beanNames) {
            Object bean = context.getBean(beanName);
            Class<?> beanClass = bean.getClass();

            for (Method method : beanClass.getDeclaredMethods()) {
                KoSubscribe sub = method.getAnnotation(KoSubscribe.class);
                if (sub == null) continue;

                String topic = sub.topic();
                String subscription = sub.name().isEmpty()
                        ? beanName + "." + method.getName()
                        : sub.name();

                if (method.getParameterCount() != 1) {
                    throw new IllegalStateException(
                            "@KoSubscribe method must have exactly one parameter: "
                                    + beanClass.getName() + "." + method.getName());
                }

                Class<?> messageType = method.getParameterTypes()[0];
                method.setAccessible(true);

                provider.subscribe(topic, subscription, messageType, message -> {
                    try {
                        method.invoke(bean, message);
                    } catch (Exception e) {
                        log.error("Ko: Failed to invoke subscriber {}.{}(): {}",
                                beanName, method.getName(), e.getMessage(), e);
                    }
                });

                log.info("Ko: Registered subscriber {} -> topic '{}'", subscription, topic);
            }
        }
    }
}

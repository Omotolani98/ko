package dev.ko.runtime.service;

import dev.ko.runtime.config.AppModelLoader;
import dev.ko.runtime.model.AppModel;
import dev.ko.runtime.model.ServiceModel;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;

/**
 * Registers @KoService classes as Spring beans during early container setup.
 */
public class KoServiceRegistrar implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        AppModel model = AppModelLoader.load();
        for (ServiceModel service : model.services()) {
            GenericBeanDefinition beanDef = new GenericBeanDefinition();
            beanDef.setBeanClassName(service.className());
            registry.registerBeanDefinition(service.name(), beanDef);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }
}

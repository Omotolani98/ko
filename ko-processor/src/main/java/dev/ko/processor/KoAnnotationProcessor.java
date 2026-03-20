package dev.ko.processor;

import dev.ko.annotations.KoService;
import dev.ko.processor.emitter.AppModelEmitter;
import dev.ko.processor.emitter.ServiceClientEmitter;
import dev.ko.processor.model.*;
import dev.ko.processor.scanner.PubSubScanner;
import dev.ko.processor.scanner.ServiceScanner;
import dev.ko.processor.validator.CronExpressionValidator;
import dev.ko.processor.validator.NamingConventionValidator;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

@SupportedAnnotationTypes("dev.ko.annotations.KoService")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedOptions("ko.app.name")
public class KoAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        Set<? extends Element> serviceElements = roundEnv.getElementsAnnotatedWith(KoService.class);
        if (serviceElements.isEmpty()) {
            return false;
        }

        ServiceScanner serviceScanner = new ServiceScanner(processingEnv);
        List<ServiceModel> services = new ArrayList<>();
        Map<String, TypeElement> serviceElements2 = new LinkedHashMap<>();
        Map<String, List<PubSubScanner.PubSubInfo>> allPubInfos = new LinkedHashMap<>();
        Map<String, List<PubSubScanner.SubscribeInfo>> allSubInfos = new LinkedHashMap<>();
        boolean hasErrors = false;

        for (Element element : serviceElements) {
            TypeElement typeElement = (TypeElement) element;
            KoService annotation = typeElement.getAnnotation(KoService.class);

            // Validate service name
            if (!NamingConventionValidator.isValidKebabCase(annotation.value())) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Service name '" + annotation.value() + "' must be kebab-case (e.g., 'my-service')",
                        typeElement
                );
                hasErrors = true;
                continue;
            }

            // Validate cron expressions
            for (var enclosed : typeElement.getEnclosedElements()) {
                var cronAnnotation = enclosed.getAnnotation(dev.ko.annotations.KoCron.class);
                if (cronAnnotation != null && !CronExpressionValidator.isValid(cronAnnotation.schedule())) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Invalid cron expression: '" + cronAnnotation.schedule() + "'",
                            enclosed
                    );
                    hasErrors = true;
                }
            }

            ServiceModel serviceModel = serviceScanner.scan(typeElement);
            services.add(serviceModel);
            serviceElements2.put(serviceModel.name(), typeElement);

            allPubInfos.put(serviceModel.name(), serviceScanner.scanPubSubInfos(typeElement));
            allSubInfos.put(serviceModel.name(), serviceScanner.scanSubscribeInfos(typeElement));
        }

        if (hasErrors) {
            return true;
        }

        // Build aggregate lists
        List<DatabaseModel> allDatabases = services.stream()
                .flatMap(s -> s.databases().stream())
                .distinct()
                .toList();

        List<PubSubTopicModel> allTopics = buildTopicModels(allPubInfos, allSubInfos);
        List<ServiceDependencyModel> dependencies = buildDependencies(allPubInfos, allSubInfos);

        String appName = processingEnv.getOptions().getOrDefault("ko.app.name", "app");

        AppModel appModel = new AppModel(
                "1.0",
                "0.1.0-SNAPSHOT",
                appName,
                Instant.now().toString(),
                services,
                allTopics,
                allDatabases,
                dependencies
        );

        try {
            new AppModelEmitter(processingEnv.getFiler()).emit(appModel);
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.NOTE,
                    "Ko: Generated ko-app-model.json (" + services.size() + " services, "
                            + allDatabases.size() + " databases, " + allTopics.size() + " topics)"
            );
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Ko: Failed to write ko-app-model.json: " + e.getMessage()
            );
        }

        // Generate service client classes
        ServiceClientEmitter clientEmitter = new ServiceClientEmitter(processingEnv.getFiler());
        for (ServiceModel service : services) {
            try {
                TypeElement typeElement = serviceElements2.get(service.name());
                clientEmitter.emit(service, typeElement);
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Ko: Failed to generate client for " + service.name() + ": " + e.getMessage()
                );
            }
        }

        return true;
    }

    private List<PubSubTopicModel> buildTopicModels(
            Map<String, List<PubSubScanner.PubSubInfo>> allPubInfos,
            Map<String, List<PubSubScanner.SubscribeInfo>> allSubInfos) {

        // Collect all topic names
        Map<String, PubSubTopicModel> topicMap = new LinkedHashMap<>();

        for (var entry : allPubInfos.entrySet()) {
            String serviceName = entry.getKey();
            for (var info : entry.getValue()) {
                topicMap.computeIfAbsent(info.topic(), name -> new PubSubTopicModel(
                        name, info.delivery(), null,
                        new ArrayList<>(), new ArrayList<>()
                ));
                ((ArrayList<String>) topicMap.get(info.topic()).publishers()).add(serviceName);
            }
        }

        for (var entry : allSubInfos.entrySet()) {
            String serviceName = entry.getKey();
            for (var info : entry.getValue()) {
                topicMap.computeIfAbsent(info.topic(), name -> new PubSubTopicModel(
                        name, "AT_LEAST_ONCE", null,
                        new ArrayList<>(), new ArrayList<>()
                ));
                ((ArrayList<SubscriberModel>) topicMap.get(info.topic()).subscribers())
                        .add(new SubscriberModel(serviceName, info.subscriptionName()));
            }
        }

        return new ArrayList<>(topicMap.values());
    }

    private List<ServiceDependencyModel> buildDependencies(
            Map<String, List<PubSubScanner.PubSubInfo>> allPubInfos,
            Map<String, List<PubSubScanner.SubscribeInfo>> allSubInfos) {

        List<ServiceDependencyModel> deps = new ArrayList<>();

        // Pub/sub dependencies: publisher -> subscriber via topic
        Map<String, List<String>> topicPublishers = new HashMap<>();
        for (var entry : allPubInfos.entrySet()) {
            for (var info : entry.getValue()) {
                topicPublishers.computeIfAbsent(info.topic(), k -> new ArrayList<>()).add(entry.getKey());
            }
        }

        for (var entry : allSubInfos.entrySet()) {
            String subscriberService = entry.getKey();
            for (var info : entry.getValue()) {
                List<String> publishers = topicPublishers.getOrDefault(info.topic(), List.of());
                for (String publisher : publishers) {
                    if (!publisher.equals(subscriberService)) {
                        deps.add(new ServiceDependencyModel(publisher, subscriberService, "pubsub", info.topic()));
                    }
                }
            }
        }

        return deps;
    }
}

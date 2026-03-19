package dev.ko.processor.scanner;

import dev.ko.annotations.*;
import dev.ko.processor.model.*;
import dev.ko.processor.util.TypeMirrorUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

public class ServiceScanner {

    private final ApiScanner apiScanner;
    private final DatabaseScanner databaseScanner;
    private final PubSubScanner pubSubScanner;
    private final CronScanner cronScanner;

    public ServiceScanner(ProcessingEnvironment processingEnv) {
        this.apiScanner = new ApiScanner(processingEnv);
        this.databaseScanner = new DatabaseScanner();
        this.pubSubScanner = new PubSubScanner();
        this.cronScanner = new CronScanner();
    }

    public ServiceModel scan(TypeElement serviceElement) {
        KoService annotation = serviceElement.getAnnotation(KoService.class);
        String serviceName = annotation.value();
        String className = serviceElement.getQualifiedName().toString();
        String packageName = className.substring(0, className.lastIndexOf('.'));

        List<ApiEndpointModel> apis = apiScanner.scan(serviceElement);
        List<DatabaseModel> databases = databaseScanner.scan(serviceElement);
        List<CronJobModel> cronJobs = cronScanner.scan(serviceElement);

        // Scan publishers and subscribers
        List<PubSubScanner.PubSubInfo> pubInfos = pubSubScanner.scanPublishers(serviceElement);
        List<PubSubScanner.SubscribeInfo> subInfos = pubSubScanner.scanSubscribers(serviceElement);

        List<String> publishes = pubInfos.stream().map(PubSubScanner.PubSubInfo::topic).toList();
        List<String> subscribes = subInfos.stream().map(PubSubScanner.SubscribeInfo::topic).toList();

        // Scan caches
        List<CacheModel> caches = scanCaches(serviceElement);

        // Scan secrets
        List<SecretModel> secrets = scanSecrets(serviceElement);

        // Scan buckets
        List<BucketModel> buckets = scanBuckets(serviceElement);

        return new ServiceModel(
                serviceName, className, packageName,
                apis, databases, publishes, subscribes,
                caches, cronJobs, secrets, buckets
        );
    }

    public List<PubSubScanner.PubSubInfo> scanPubSubInfos(TypeElement serviceElement) {
        return pubSubScanner.scanPublishers(serviceElement);
    }

    public List<PubSubScanner.SubscribeInfo> scanSubscribeInfos(TypeElement serviceElement) {
        return pubSubScanner.scanSubscribers(serviceElement);
    }

    private List<CacheModel> scanCaches(TypeElement serviceElement) {
        List<CacheModel> caches = new ArrayList<>();
        for (var enclosed : serviceElement.getEnclosedElements()) {
            KoCache cacheAnnotation = enclosed.getAnnotation(KoCache.class);
            if (cacheAnnotation != null) {
                String keyType;
                try {
                    keyType = cacheAnnotation.keyType().getName();
                } catch (javax.lang.model.type.MirroredTypeException e) {
                    keyType = TypeMirrorUtils.getQualifiedName(e.getTypeMirror());
                }
                caches.add(new CacheModel(
                        cacheAnnotation.name(),
                        keyType,
                        cacheAnnotation.ttl()
                ));
            }
        }
        return caches;
    }

    private List<SecretModel> scanSecrets(TypeElement serviceElement) {
        List<SecretModel> secrets = new ArrayList<>();
        for (var enclosed : serviceElement.getEnclosedElements()) {
            KoSecret secretAnnotation = enclosed.getAnnotation(KoSecret.class);
            if (secretAnnotation != null) {
                secrets.add(new SecretModel(secretAnnotation.value()));
            }
        }
        return secrets;
    }

    private List<BucketModel> scanBuckets(TypeElement serviceElement) {
        List<BucketModel> buckets = new ArrayList<>();
        for (var enclosed : serviceElement.getEnclosedElements()) {
            KoBucket bucketAnnotation = enclosed.getAnnotation(KoBucket.class);
            if (bucketAnnotation != null) {
                buckets.add(new BucketModel(
                        bucketAnnotation.name(),
                        bucketAnnotation.publicRead()
                ));
            }
        }
        return buckets;
    }
}

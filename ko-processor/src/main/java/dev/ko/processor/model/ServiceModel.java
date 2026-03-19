package dev.ko.processor.model;

import java.util.List;

public record ServiceModel(
        String name,
        String className,
        String packageName,
        List<ApiEndpointModel> apis,
        List<DatabaseModel> databases,
        List<String> publishes,
        List<String> subscribes,
        List<CacheModel> caches,
        List<CronJobModel> cronJobs,
        List<SecretModel> secrets,
        List<BucketModel> buckets
) {}

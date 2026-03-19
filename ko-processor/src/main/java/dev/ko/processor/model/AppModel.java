package dev.ko.processor.model;

import java.util.List;

public record AppModel(
        String schema,
        String version,
        String app,
        String generatedAt,
        List<ServiceModel> services,
        List<PubSubTopicModel> pubsubTopics,
        List<DatabaseModel> databases,
        List<ServiceDependencyModel> serviceDependencies
) {}

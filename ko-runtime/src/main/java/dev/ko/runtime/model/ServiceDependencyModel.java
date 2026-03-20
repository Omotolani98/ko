package dev.ko.runtime.model;

public record ServiceDependencyModel(
        String from,
        String to,
        String type,
        String topic
) {}

package dev.ko.processor.model;

public record ServiceDependencyModel(
        String from,
        String to,
        String type,
        String topic
) {}

package dev.ko.processor.model;

public record CacheModel(
        String name,
        String keyType,
        long ttl
) {}

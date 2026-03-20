package dev.ko.runtime.model;

public record CacheModel(
        String name,
        String keyType,
        long ttl
) {}

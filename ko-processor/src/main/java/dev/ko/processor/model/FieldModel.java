package dev.ko.processor.model;

public record FieldModel(
        String name,
        String type,
        boolean required
) {}

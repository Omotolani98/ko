package dev.ko.processor.model;

import java.util.List;

public record TypeModel(
        String className,
        List<FieldModel> fields
) {}

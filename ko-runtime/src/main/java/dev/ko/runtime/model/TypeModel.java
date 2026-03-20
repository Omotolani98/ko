package dev.ko.runtime.model;

import java.util.List;

public record TypeModel(
        String className,
        List<FieldModel> fields
) {}

package dev.ko.processor.model;

import java.util.List;

public record ApiEndpointModel(
        String name,
        String method,
        String path,
        boolean auth,
        List<String> permissions,
        boolean expose,
        TypeModel requestType,
        TypeModel responseType,
        String javadoc
) {}

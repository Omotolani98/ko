package dev.ko.processor.model;

public record CronJobModel(
        String name,
        String schedule,
        String method
) {}

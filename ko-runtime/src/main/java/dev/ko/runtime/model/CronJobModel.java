package dev.ko.runtime.model;

public record CronJobModel(
        String name,
        String schedule,
        String method
) {}

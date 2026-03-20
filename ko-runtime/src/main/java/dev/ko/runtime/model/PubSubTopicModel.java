package dev.ko.runtime.model;

import java.util.List;

public record PubSubTopicModel(
        String name,
        String delivery,
        TypeModel messageType,
        List<String> publishers,
        List<SubscriberModel> subscribers
) {}

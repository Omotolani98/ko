package dev.ko.processor.scanner;

import dev.ko.annotations.KoPubSub;
import dev.ko.annotations.KoSubscribe;
import dev.ko.processor.model.TypeModel;
import dev.ko.processor.util.TypeMirrorUtils;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

public class PubSubScanner {

    public record PubSubInfo(String topic, String delivery, TypeMirror messageType) {}
    public record SubscribeInfo(String topic, String subscriptionName, String methodName) {}

    public List<PubSubInfo> scanPublishers(TypeElement serviceElement) {
        List<PubSubInfo> topics = new ArrayList<>();

        for (var enclosed : serviceElement.getEnclosedElements()) {
            KoPubSub pubSubAnnotation = enclosed.getAnnotation(KoPubSub.class);
            if (pubSubAnnotation == null) {
                continue;
            }

            // Extract generic type argument from the field type
            TypeMirror fieldType = enclosed.asType();
            List<? extends TypeMirror> typeArgs = TypeMirrorUtils.getTypeArguments(fieldType);
            TypeMirror messageType = typeArgs.isEmpty() ? null : typeArgs.getFirst();

            topics.add(new PubSubInfo(
                    pubSubAnnotation.topic(),
                    pubSubAnnotation.delivery().name(),
                    messageType
            ));
        }

        return topics;
    }

    public List<SubscribeInfo> scanSubscribers(TypeElement serviceElement) {
        List<SubscribeInfo> subscribers = new ArrayList<>();

        for (var enclosed : serviceElement.getEnclosedElements()) {
            KoSubscribe subAnnotation = enclosed.getAnnotation(KoSubscribe.class);
            if (subAnnotation == null) {
                continue;
            }

            ExecutableElement method = (ExecutableElement) enclosed;
            String name = subAnnotation.name().isEmpty()
                    ? method.getSimpleName().toString()
                    : subAnnotation.name();

            subscribers.add(new SubscribeInfo(
                    subAnnotation.topic(),
                    name,
                    method.getSimpleName().toString()
            ));
        }

        return subscribers;
    }
}

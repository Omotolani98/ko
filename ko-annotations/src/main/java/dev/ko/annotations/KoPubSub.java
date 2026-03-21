package dev.ko.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a pub/sub topic resource within a {@link KoService}.
 * Applied to a {@code KoTopic} field.
 *
 * <pre>{@code
 * @KoPubSub(topic = "user-events", delivery = DeliveryGuarantee.EXACTLY_ONCE)
 * private KoTopic<UserEvent> events;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoPubSub {
    /**
     * The name of the pub/sub topic.
     *
     * @return the topic name
     */
    String topic();

    /**
     * The delivery guarantee for this topic.
     *
     * @return the delivery guarantee, defaults to {@link DeliveryGuarantee#AT_LEAST_ONCE}
     */
    DeliveryGuarantee delivery() default DeliveryGuarantee.AT_LEAST_ONCE;
}

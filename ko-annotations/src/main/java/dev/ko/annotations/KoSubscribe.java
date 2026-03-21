package dev.ko.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a subscriber to a pub/sub topic.
 * The method receives messages published to the specified topic.
 *
 * <pre>{@code
 * @KoSubscribe(topic = "user-events", name = "send-welcome-email")
 * public void onUserCreated(UserEvent event) {
 *     // handle event
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoSubscribe {
    /**
     * The name of the topic to subscribe to.
     *
     * @return the topic name
     */
    String topic();

    /**
     * The subscription name. Defaults to the method name if empty.
     *
     * @return the subscription name
     */
    String name() default "";

    /**
     * The maximum number of delivery retries before sending to the dead-letter queue.
     *
     * @return the max retry count, defaults to {@code 3}
     */
    int maxRetries() default 3;

    /**
     * Whether failed messages should be sent to a dead-letter queue after exhausting retries.
     *
     * @return {@code true} to enable dead-letter queue, defaults to {@code true}
     */
    boolean deadLetter() default true;
}

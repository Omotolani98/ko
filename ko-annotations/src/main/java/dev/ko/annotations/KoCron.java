package dev.ko.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a scheduled cron job within a {@link KoService}.
 * The method is invoked on the specified schedule.
 *
 * <pre>{@code
 * @KoCron(schedule = "0 0 * * *", name = "daily-cleanup")
 * public void cleanupExpiredSessions() {
 *     // runs daily at midnight
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoCron {
    /**
     * The cron expression defining the schedule (standard 5-field cron syntax).
     *
     * @return the cron schedule expression
     */
    String schedule();

    /**
     * The name of this cron job. Defaults to the method name if empty.
     *
     * @return the cron job name
     */
    String name() default "";
}

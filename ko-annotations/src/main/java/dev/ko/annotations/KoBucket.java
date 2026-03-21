package dev.ko.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an object storage bucket resource within a {@link KoService}.
 * Applied to a {@code KoBucketStore} field.
 *
 * <pre>{@code
 * @KoBucket(name = "avatars", publicRead = true)
 * private KoBucketStore avatars;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoBucket {
    /**
     * The logical name of the storage bucket.
     *
     * @return the bucket name
     */
    String name();

    /**
     * Whether objects in this bucket are publicly readable.
     *
     * @return {@code true} for public read access, defaults to {@code false}
     */
    boolean publicRead() default false;
}

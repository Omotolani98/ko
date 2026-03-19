package dev.ko.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoSubscribe {
    String topic();
    String name() default "";
    int maxRetries() default 3;
    boolean deadLetter() default true;
}

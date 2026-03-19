package dev.ko.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoAPI {
    String method() default "GET";
    String path();
    boolean auth() default false;
    String[] permissions() default {};
    boolean expose() default true;
}

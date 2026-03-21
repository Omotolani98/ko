package dev.ko.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a SQL database resource within a {@link KoService}.
 * Applied to a {@code KoSQLDatabase} field.
 * The framework provisions the database and runs migrations automatically.
 *
 * <pre>{@code
 * @KoDatabase(name = "users", migrations = "db/users")
 * private KoSQLDatabase db;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KoDatabase {
    /**
     * The logical name of the database.
     *
     * @return the database name
     */
    String name();

    /**
     * The classpath directory containing SQL migration files.
     *
     * @return the migrations directory path, defaults to {@code "migrations"}
     */
    String migrations() default "migrations";
}

package dev.ko.runtime.database;

/**
 * Runtime exception for Ko database operations.
 */
public class KoDatabaseException extends RuntimeException {

    public KoDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}

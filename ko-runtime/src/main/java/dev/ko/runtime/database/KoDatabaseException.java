package dev.ko.runtime.database;

import dev.ko.runtime.errors.KoError;
import dev.ko.runtime.errors.KoErrorCode;

/**
 * Runtime exception for Ko database operations.
 * Extends KoError so database errors produce structured API responses.
 */
public class KoDatabaseException extends KoError {

    public KoDatabaseException(String message, Throwable cause) {
        super(KoErrorCode.INTERNAL, message, cause);
    }
}

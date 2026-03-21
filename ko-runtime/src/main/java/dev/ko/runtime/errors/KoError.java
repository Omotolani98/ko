package dev.ko.runtime.errors;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured framework error type for the Ko framework.
 * Thrown from service code to produce typed HTTP error responses.
 *
 * <pre>{@code
 * throw KoError.notFound("User not found: " + id);
 * throw KoError.badRequest("Email is required");
 * throw KoError.of(KoErrorCode.CONFLICT, "Username already taken")
 *               .with("field", "username");
 * }</pre>
 */
public class KoError extends RuntimeException {

    private final KoErrorCode errorCode;
    private final Map<String, Object> metadata;

    /**
     * Creates a new KoError with the given error code and message.
     *
     * @param errorCode the error code
     * @param message the error message
     */
    public KoError(KoErrorCode errorCode, String message) {
        this(errorCode, message, null, Collections.emptyMap());
    }

    /**
     * Creates a new KoError with the given error code, message, and cause.
     *
     * @param errorCode the error code
     * @param message the error message
     * @param cause the underlying cause
     */
    public KoError(KoErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, Collections.emptyMap());
    }

    private KoError(KoErrorCode errorCode, String message, Throwable cause, Map<String, Object> metadata) {
        super(message, cause);
        this.errorCode = errorCode;
        this.metadata = metadata;
    }

    /**
     * Returns the error code.
     *
     * @return the error code
     */
    public KoErrorCode errorCode() {
        return errorCode;
    }

    /**
     * Returns the error metadata.
     *
     * @return unmodifiable map of metadata key-value pairs
     */
    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * Return a new KoError with additional metadata attached.
     */
    public KoError with(String key, Object value) {
        var newMeta = new LinkedHashMap<>(this.metadata);
        newMeta.put(key, value);
        return new KoError(errorCode, getMessage(), getCause(), Collections.unmodifiableMap(newMeta));
    }

    // --- Static factory methods ---

    /**
     * Creates a 404 Not Found error.
     *
     * @param message the error message
     * @return a new KoError
     */
    public static KoError notFound(String message) {
        return new KoError(KoErrorCode.NOT_FOUND, message);
    }

    /**
     * Creates a 400 Bad Request error.
     *
     * @param message the error message
     * @return a new KoError
     */
    public static KoError badRequest(String message) {
        return new KoError(KoErrorCode.BAD_REQUEST, message);
    }

    /**
     * Creates a 500 Internal error.
     *
     * @param message the error message
     * @return a new KoError
     */
    public static KoError internal(String message) {
        return new KoError(KoErrorCode.INTERNAL, message);
    }

    /**
     * Creates a 500 Internal error with a cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     * @return a new KoError
     */
    public static KoError internal(String message, Throwable cause) {
        return new KoError(KoErrorCode.INTERNAL, message, cause);
    }

    /**
     * Creates a 403 Permission Denied error.
     *
     * @param message the error message
     * @return a new KoError
     */
    public static KoError permissionDenied(String message) {
        return new KoError(KoErrorCode.PERMISSION_DENIED, message);
    }

    /**
     * Creates a 401 Unauthenticated error.
     *
     * @param message the error message
     * @return a new KoError
     */
    public static KoError unauthenticated(String message) {
        return new KoError(KoErrorCode.UNAUTHENTICATED, message);
    }

    /**
     * Creates a 409 Conflict error.
     *
     * @param message the error message
     * @return a new KoError
     */
    public static KoError conflict(String message) {
        return new KoError(KoErrorCode.CONFLICT, message);
    }

    /**
     * Creates a 422 Validation Failed error.
     *
     * @param message the error message
     * @return a new KoError
     */
    public static KoError validationFailed(String message) {
        return new KoError(KoErrorCode.VALIDATION_FAILED, message);
    }

    /**
     * Creates a 503 Unavailable error.
     *
     * @param message the error message
     * @return a new KoError
     */
    public static KoError unavailable(String message) {
        return new KoError(KoErrorCode.UNAVAILABLE, message);
    }

    /**
     * Creates a KoError with a custom error code.
     *
     * @param code the error code
     * @param message the error message
     * @return a new KoError
     */
    public static KoError of(KoErrorCode code, String message) {
        return new KoError(code, message);
    }

    /**
     * Creates a KoError with a custom error code and cause.
     *
     * @param code the error code
     * @param message the error message
     * @param cause the underlying cause
     * @return a new KoError
     */
    public static KoError of(KoErrorCode code, String message, Throwable cause) {
        return new KoError(code, message, cause);
    }
}

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

    public KoError(KoErrorCode errorCode, String message) {
        this(errorCode, message, null, Collections.emptyMap());
    }

    public KoError(KoErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, Collections.emptyMap());
    }

    private KoError(KoErrorCode errorCode, String message, Throwable cause, Map<String, Object> metadata) {
        super(message, cause);
        this.errorCode = errorCode;
        this.metadata = metadata;
    }

    public KoErrorCode errorCode() {
        return errorCode;
    }

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

    public static KoError notFound(String message) {
        return new KoError(KoErrorCode.NOT_FOUND, message);
    }

    public static KoError badRequest(String message) {
        return new KoError(KoErrorCode.BAD_REQUEST, message);
    }

    public static KoError internal(String message) {
        return new KoError(KoErrorCode.INTERNAL, message);
    }

    public static KoError internal(String message, Throwable cause) {
        return new KoError(KoErrorCode.INTERNAL, message, cause);
    }

    public static KoError permissionDenied(String message) {
        return new KoError(KoErrorCode.PERMISSION_DENIED, message);
    }

    public static KoError unauthenticated(String message) {
        return new KoError(KoErrorCode.UNAUTHENTICATED, message);
    }

    public static KoError conflict(String message) {
        return new KoError(KoErrorCode.CONFLICT, message);
    }

    public static KoError validationFailed(String message) {
        return new KoError(KoErrorCode.VALIDATION_FAILED, message);
    }

    public static KoError unavailable(String message) {
        return new KoError(KoErrorCode.UNAVAILABLE, message);
    }

    public static KoError of(KoErrorCode code, String message) {
        return new KoError(code, message);
    }

    public static KoError of(KoErrorCode code, String message, Throwable cause) {
        return new KoError(code, message, cause);
    }
}

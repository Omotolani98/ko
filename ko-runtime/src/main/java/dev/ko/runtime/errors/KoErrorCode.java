package dev.ko.runtime.errors;

/**
 * Standard error codes for the Ko framework.
 * Maps to HTTP status codes for API responses.
 */
public enum KoErrorCode {

    /** 400 — The request was invalid or malformed. */
    BAD_REQUEST(400, "bad_request"),

    /** 401 — Authentication is required. */
    UNAUTHENTICATED(401, "unauthenticated"),

    /** 403 — The caller does not have permission. */
    PERMISSION_DENIED(403, "permission_denied"),

    /** 404 — The requested resource was not found. */
    NOT_FOUND(404, "not_found"),

    /** 409 — The request conflicts with current state. */
    CONFLICT(409, "conflict"),

    /** 422 — The request was well-formed but semantically invalid. */
    VALIDATION_FAILED(422, "validation_failed"),

    /** 429 — Too many requests. */
    RATE_LIMITED(429, "rate_limited"),

    /** 500 — An internal error occurred. */
    INTERNAL(500, "internal"),

    /** 503 — The service is temporarily unavailable. */
    UNAVAILABLE(503, "unavailable");

    private final int httpStatus;
    private final String code;

    /**
     * Creates an error code.
     *
     * @param httpStatus the HTTP status code
     * @param code the string error code
     */
    KoErrorCode(int httpStatus, String code) {
        this.httpStatus = httpStatus;
        this.code = code;
    }

    /**
     * Returns the HTTP status code.
     *
     * @return the HTTP status code
     */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * Returns the string error code.
     *
     * @return the error code string
     */
    public String code() {
        return code;
    }
}

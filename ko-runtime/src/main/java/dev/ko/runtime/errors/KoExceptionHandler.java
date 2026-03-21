package dev.ko.runtime.errors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for Ko API endpoints.
 * Converts exceptions into structured JSON error responses.
 *
 * <p>Response format:</p>
 * <pre>{@code
 * {
 *   "code": "not_found",
 *   "message": "User not found: abc123",
 *   "metadata": {},
 *   "timestamp": "2026-03-20T22:30:00Z"
 * }
 * }</pre>
 */
@ControllerAdvice
public class KoExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(KoExceptionHandler.class);

    @ExceptionHandler(KoError.class)
    public ResponseEntity<Map<String, Object>> handleKoError(KoError error) {
        HttpStatus status = HttpStatus.resolve(error.errorCode().httpStatus());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (status.is5xxServerError()) {
            log.error("Ko: {} — {}", error.errorCode().code(), error.getMessage(), error);
        } else {
            log.warn("Ko: {} — {}", error.errorCode().code(), error.getMessage());
        }

        return ResponseEntity
                .status(status)
                .body(buildBody(error.errorCode().code(), error.getMessage(), error.metadata()));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(buildBody("not_found", "The requested resource was not found", Map.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Ko: bad_request — {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildBody("bad_request", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Ko: Unhandled exception", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildBody("internal", "An internal error occurred", Map.of()));
    }

    private static Map<String, Object> buildBody(String code, String message, Map<String, Object> metadata) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        if (metadata != null && !metadata.isEmpty()) {
            body.put("metadata", metadata);
        }
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}

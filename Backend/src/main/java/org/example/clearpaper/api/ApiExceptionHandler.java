package org.example.clearpaper.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(
                        new ApiError(
                                "VALIDATION_ERROR",
                                "Check the highlighted fields.",
                                fields,
                                Instant.now()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed() {
        return response(400, "INVALID_JSON", "Send a valid JSON request body.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> credentials() {
        return response(401, "INVALID_CREDENTIALS", "Email or password is incorrect.");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiError> duplicate() {
        return response(409, "RECORD_CONFLICT", "A record already exists with these details.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        return response(
                status,
                status == 404 ? "NOT_FOUND" : "REQUEST_ERROR",
                status == 404
                        ? "Resource not found."
                        : status == 400 && ex.getReason() != null
                                ? ex.getReason()
                                : "Request could not be completed.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missing() {
        return response(404, "NOT_FOUND", "Resource not found.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method() {
        return response(405, "METHOD_NOT_ALLOWED", "This request method is not supported.");
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> media() {
        return response(415, "UNSUPPORTED_MEDIA_TYPE", "Use Content-Type: application/json.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("Unhandled application error", ex);
        return response(500, "INTERNAL_ERROR", "Something went wrong. Please try again.");
    }

    private ResponseEntity<ApiError> response(int status, String code, String message) {
        return ResponseEntity.status(status).body(ApiError.of(code, message));
    }
}

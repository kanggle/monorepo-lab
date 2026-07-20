package com.example.fanplatform.membership.presentation.advice;

import com.example.common.persistence.DataIntegrityViolations;
import com.example.fanplatform.membership.presentation.dto.ApiErrorBody;
import jakarta.persistence.OptimisticLockException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Cross-cutting exception handlers shared across membership-service controllers.
 * Service-specific handlers live in {@link GlobalExceptionHandler}.
 *
 * <p>Service-local (NOT in {@code libs/}) per {@code platform/shared-library-policy.md}
 * — mirrors community-service / artist-service.
 */
@Slf4j
abstract class AbstractDomainExceptionHandler {

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiErrorBody> handleOptimisticLock(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("CONFLICT", "Concurrent modification detected. Please retry."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorBody> handleIntegrity(DataIntegrityViolationException e) {
        if (DataIntegrityViolations.isUniqueViolation(e)) {
            // Unique violation = client-visible conflict → 409 (the registry catch-all).
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiErrorBody.of("CONFLICT", "Data integrity violation"));
        }
        // FK / NOT NULL / CHECK violations are SERVER defects, not client conflicts.
        // Kept as 500 so they stay loud in logs + alerting (TASK-MONO-450 / TASK-BE-542).
        log.error("Non-unique data integrity violation", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorBody.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorBody> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("VALIDATION_ERROR", message));
    }

    /** Missing {@code Idempotency-Key} (or any required header) → 400 VALIDATION_ERROR. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorBody> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorBody.of("VALIDATION_ERROR", "Missing required header: " + e.getHeaderName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorBody> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorBody.of("VALIDATION_ERROR", "Invalid parameter: " + e.getName()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("VALIDATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalState(IllegalStateException e) {
        log.warn("illegal state at controller boundary", e);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("ILLEGAL_STATE", e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorBody> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("NOT_FOUND", "The requested resource was not found"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorBody> handleNoHandlerFound(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("NOT_FOUND", "The requested resource was not found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorBody> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            builder.allow(supported.toArray(new HttpMethod[0]));
        }
        return builder.body(ApiErrorBody.of("METHOD_NOT_ALLOWED",
                "HTTP method not supported for this endpoint"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorBody> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiErrorBody.of("UNSUPPORTED_MEDIA_TYPE",
                        "Request Content-Type is not supported by this endpoint"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorBody> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorBody.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

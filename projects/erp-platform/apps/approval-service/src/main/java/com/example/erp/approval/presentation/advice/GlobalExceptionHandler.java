package com.example.erp.approval.presentation.advice;

import com.example.erp.approval.domain.error.ApprovalDomainException;
import com.example.erp.approval.presentation.dto.ApiErrorBody;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * Maps erp approval domain exceptions to the approval-api.md error envelope.
 * The authoritative code→HTTP table lives in approval-api.md § Error code → HTTP;
 * {@link #STATUS_BY_CODE} is the exhaustive mechanical mirror.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** approval-api.md § Error code → HTTP status (verbatim). */
    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.ofEntries(
            Map.entry("VALIDATION_ERROR", HttpStatus.BAD_REQUEST),
            Map.entry("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST),
            Map.entry("IDEMPOTENCY_KEY_CONFLICT", HttpStatus.CONFLICT),
            Map.entry("APPROVAL_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("APPROVAL_STATUS_TRANSITION_INVALID", HttpStatus.CONFLICT),
            Map.entry("APPROVAL_ALREADY_FINALIZED", HttpStatus.CONFLICT),
            Map.entry("APPROVAL_NOT_AUTHORIZED_APPROVER", HttpStatus.FORBIDDEN),
            Map.entry("APPROVAL_ROUTE_INVALID", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("DELEGATION_INVALID", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("DELEGATION_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("PERMISSION_DENIED", HttpStatus.FORBIDDEN),
            Map.entry("DATA_SCOPE_FORBIDDEN", HttpStatus.FORBIDDEN),
            Map.entry("TENANT_FORBIDDEN", HttpStatus.FORBIDDEN),
            Map.entry("EXTERNAL_TRAFFIC_REJECTED", HttpStatus.FORBIDDEN),
            Map.entry("UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
            Map.entry("CONCURRENT_MODIFICATION", HttpStatus.CONFLICT),
            Map.entry("IDEMPOTENCY_STORE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry("ILLEGAL_STATE", HttpStatus.UNPROCESSABLE_ENTITY));

    /**
     * Resolve a code's status from {@link #STATUS_BY_CODE} — the single place a
     * code→status pair is decided. Handlers that <i>mint</i> a code (rather than
     * carry one on an {@link ApprovalDomainException}) route through here too, so one
     * code can never leave this service at two different statuses.
     *
     * <p>{@link #handleGeneral} is deliberately NOT routed here: {@code INTERNAL_ERROR}
     * is absent from the table, so the {@code getOrDefault} fallback would turn its
     * 500 into a 422.
     */
    private ResponseEntity<ApiErrorBody> respond(String code, String message) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(code, HttpStatus.UNPROCESSABLE_ENTITY);
        return ResponseEntity.status(status).body(ApiErrorBody.of(code, message));
    }

    @ExceptionHandler(ApprovalDomainException.class)
    public ResponseEntity<ApiErrorBody> handleDomain(ApprovalDomainException e) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(e.code(),
                HttpStatus.UNPROCESSABLE_ENTITY);
        if (status.is5xxServerError()) {
            log.warn("domain failure {} -> {}: {}", e.code(), status, e.getMessage());
        }
        return ResponseEntity.status(status).body(ApiErrorBody.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorBody> handleMissingHeader(MissingRequestHeaderException e) {
        if ("Idempotency-Key".equalsIgnoreCase(e.getHeaderName())) {
            return respond("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key header is required for mutating endpoints");
        }
        return respond("VALIDATION_ERROR", "Missing required header: " + e.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorBody> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return respond("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalArgument(IllegalArgumentException e) {
        return respond("VALIDATION_ERROR", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorBody> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return respond("VALIDATION_ERROR", "Invalid parameter: " + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorBody> handleMalformed(HttpMessageNotReadableException e) {
        return respond("VALIDATION_ERROR", "Malformed request body");
    }

    @ExceptionHandler({OptimisticLockException.class,
            ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiErrorBody> handleOptimisticLock(Exception e) {
        return respond("CONCURRENT_MODIFICATION",
                "Concurrent modification detected. Please retry.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalState(IllegalStateException e) {
        log.warn("illegal state at controller boundary", e);
        return respond("ILLEGAL_STATE", e.getMessage());
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorBody> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorBody.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

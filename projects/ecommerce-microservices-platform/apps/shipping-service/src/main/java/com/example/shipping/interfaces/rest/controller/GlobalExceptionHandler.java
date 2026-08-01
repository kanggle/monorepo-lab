package com.example.shipping.interfaces.rest.controller;

import com.example.common.persistence.DataIntegrityViolations;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.AccessDeniedException;
import com.example.web.exception.CommonGlobalExceptionHandler;
import com.example.shipping.application.exception.UnauthorizedShippingAccessException;
import com.example.shipping.application.exception.WebhookSignatureException;
import com.example.shipping.domain.exception.InvalidShippingException;
import com.example.shipping.domain.exception.InvalidStatusTransitionException;
import com.example.shipping.domain.exception.ShippingNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Non-domain arms (404/405/415/generic catch-all) come from
 * {@link CommonGlobalExceptionHandler} (ADR-MONO-058 § D2). {@link #handleValidation}
 * (no field-name prefix), {@link #handleMissingHeader} ({@code X-User-Id}/
 * {@code X-User-Role} 401 special-casing), and {@link #handleIllegalArgument}
 * ({@code INVALID_SHIPPING_REQUEST} code) all diverge from the shared handler's
 * defaults, so they stay local — as true Java overrides (same name/signature/return
 * type as the shared method, re-declaring {@code @ExceptionHandler}), not
 * differently-named methods, since Spring's resolver throws {@code
 * IllegalStateException: Ambiguous @ExceptionHandler} if two methods (inherited +
 * local) map the same exact exception type.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    @Override
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("Invalid input value");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .findFirst()
                .orElse("Invalid input value");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    @Override
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        String headerName = e.getHeaderName();
        if ("X-User-Id".equalsIgnoreCase(headerName)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.of("UNAUTHORIZED", "Missing or invalid access token"));
        }
        if ("X-User-Role".equalsIgnoreCase(headerName)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.of("UNAUTHORIZED", "Missing or invalid access token"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_SHIPPING_REQUEST", headerName + " header is required"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED", e.getMessage()));
    }

    @ExceptionHandler(UnauthorizedShippingAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(UnauthorizedShippingAccessException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED", e.getMessage()));
    }

    @ExceptionHandler(WebhookSignatureException.class)
    public ResponseEntity<ErrorResponse> handleWebhookSignature(WebhookSignatureException e) {
        log.warn("Carrier webhook rejected: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("WEBHOOK_SIGNATURE_INVALID", "Webhook signature verification failed"));
    }

    @ExceptionHandler(ShippingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleShippingNotFound(ShippingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("SHIPPING_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(InvalidShippingException.class)
    public ResponseEntity<ErrorResponse> handleInvalidShipping(InvalidShippingException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_SHIPPING_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(InvalidStatusTransitionException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("INVALID_STATUS_TRANSITION", e.getMessage()));
    }

    @Override
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_SHIPPING_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        if (DataIntegrityViolations.isUniqueViolation(e)) {
            // A duplicate is a client-visible conflict: the registry's declared catch-all.
            log.warn("Unique constraint violation → 409", e);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of("DATA_INTEGRITY_VIOLATION", "Data integrity violation"));
        }
        // FK / NOT NULL / CHECK violations are SERVER defects, not client conflicts.
        // Deliberately left as 500 so they stay loud in logs and alerting (TASK-BE-542 AC-1).
        log.error("Non-unique data integrity violation", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

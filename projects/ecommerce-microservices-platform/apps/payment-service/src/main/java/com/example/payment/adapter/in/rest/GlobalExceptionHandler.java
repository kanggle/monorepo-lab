package com.example.payment.adapter.in.rest;

import com.example.common.persistence.DataIntegrityViolations;
import com.example.web.dto.ErrorResponse;
import com.example.payment.application.exception.AmountMismatchException;
import com.example.payment.application.exception.IdempotencyKeyRequiredException;
import com.example.payment.application.exception.IdempotencyKeyConflictException;
import com.example.payment.application.exception.PaymentAlreadyCompletedException;
import com.example.payment.application.exception.UnauthorizedPaymentAccessException;
import com.example.libs.payment.PgConfirmFailedException;
import com.example.libs.payment.PgGatewayUnavailableException;
import com.example.payment.domain.exception.InvalidPaymentException;
import com.example.payment.domain.exception.PaymentNotFoundException;
import com.example.web.exception.CommonGlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Non-domain arms (malformed-body/404/405/415) come from
 * {@link CommonGlobalExceptionHandler} (ADR-MONO-058 § D2). {@link #handleGeneral}
 * stays local — it answers {@code "An internal server error occurred"}, matching this
 * service's own {@link #handleDataIntegrityViolation} non-unique 500 branch, not the
 * shared handler's {@code "An unexpected error occurred"}. It is declared as a true
 * Java override (same name/signature/return type as the shared method, re-declaring
 * {@code @ExceptionHandler}) rather than a differently-named method, since Spring's
 * resolver throws {@code IllegalStateException: Ambiguous @ExceptionHandler} if two
 * methods (inherited + local) map the same exact exception type.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("PAYMENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(InvalidPaymentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPayment(InvalidPaymentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_PAYMENT_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(UnauthorizedPaymentAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedPaymentAccessException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED", e.getMessage()));
    }

    @ExceptionHandler(PgConfirmFailedException.class)
    public ResponseEntity<ErrorResponse> handlePgConfirmFailed(PgConfirmFailedException e) {
        log.warn("PG confirm failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of("PG_CONFIRM_FAILED", e.getMessage()));
    }

    @ExceptionHandler(PgGatewayUnavailableException.class)
    public ResponseEntity<ErrorResponse> handlePgGatewayUnavailable(PgGatewayUnavailableException e) {
        log.warn("PG gateway unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("PG_GATEWAY_UNAVAILABLE", e.getMessage()));
    }

    @ExceptionHandler(AmountMismatchException.class)
    public ResponseEntity<ErrorResponse> handleAmountMismatch(AmountMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("AMOUNT_MISMATCH", e.getMessage()));
    }

    @ExceptionHandler(PaymentAlreadyCompletedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyCompleted(PaymentAlreadyCompletedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("PAYMENT_ALREADY_COMPLETED", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyRequiredException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyRequired(IdempotencyKeyRequiredException e) {
        // 400 IDEMPOTENCY_KEY_REQUIRED — funds-out path refuses a keyless request rather
        // than serving it non-idempotently (TASK-BE-535).
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("IDEMPOTENCY_KEY_REQUIRED", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyConflict(IdempotencyKeyConflictException e) {
        // 409 IDEMPOTENCY_KEY_CONFLICT — same key replayed with a different amount, or the
        // loser of a concurrent same-key insert race (TASK-BE-535).
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("IDEMPOTENCY_KEY_CONFLICT", e.getMessage()));
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
                .body(ErrorResponse.of("INTERNAL_ERROR", "An internal server error occurred"));
    }

    @Override
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An internal server error occurred"));
    }
}

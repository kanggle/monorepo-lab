package com.example.settlement.presentation;

import com.example.settlement.application.exception.SellerScopeForbiddenException;
import com.example.settlement.domain.model.InvalidCommissionRateException;
import com.example.settlement.domain.period.PeriodAlreadyClosedException;
import com.example.settlement.domain.period.PeriodAlreadyOpenException;
import com.example.settlement.domain.period.PeriodNotClosedException;
import com.example.settlement.domain.period.PeriodNotFoundException;
import com.example.settlement.domain.period.PeriodWindowInvalidException;
import com.example.common.persistence.DataIntegrityViolations;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.AccessDeniedException;
import com.example.web.exception.CommonGlobalExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps settlement exceptions to the settlement-api.md error contract:
 * {@code COMMISSION_RATE_INVALID} (422), {@code SETTLEMENT_NOT_FOUND} (404),
 * {@code ACCESS_DENIED} (403). Validation failures → 400 {@code VALIDATION_ERROR}.
 *
 * <p>Non-domain arms (malformed-body/404/405/415/generic catch-all) come from
 * {@link CommonGlobalExceptionHandler} (ADR-MONO-058 § D2). {@link #handleValidation}
 * (no field-name prefix) and {@link #handleMissingParam} (message text order differs
 * from the shared handler's) stay local — as true Java overrides (same
 * name/signature/return type as the shared method, re-declaring
 * {@code @ExceptionHandler}), not differently-named methods, since Spring's resolver
 * throws {@code IllegalStateException: Ambiguous @ExceptionHandler} if two methods
 * (inherited + local) map the same exact exception type.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    @ExceptionHandler(InvalidCommissionRateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRate(InvalidCommissionRateException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("COMMISSION_RATE_INVALID", e.getMessage()));
    }

    @ExceptionHandler(SellerScopeForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleSellerScope(SellerScopeForbiddenException e) {
        // 404-over-403 — no cross-tenant / cross-seller existence disclosure (M3).
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("SETTLEMENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(PeriodWindowInvalidException.class)
    public ResponseEntity<ErrorResponse> handlePeriodWindow(PeriodWindowInvalidException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("PERIOD_WINDOW_INVALID", e.getMessage()));
    }

    @ExceptionHandler(PeriodAlreadyOpenException.class)
    public ResponseEntity<ErrorResponse> handlePeriodAlreadyOpen(PeriodAlreadyOpenException e) {
        // 409 PERIOD_ALREADY_OPEN — duplicate POST /periods for a window an OPEN period
        // already covers exactly (TASK-BE-535). Only exact duplicates; overlapping
        // windows and a re-open after close remain allowed.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("PERIOD_ALREADY_OPEN", e.getMessage()));
    }

    @ExceptionHandler(PeriodAlreadyClosedException.class)
    public ResponseEntity<ErrorResponse> handlePeriodAlreadyClosed(PeriodAlreadyClosedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("PERIOD_ALREADY_CLOSED", e.getMessage()));
    }

    @ExceptionHandler(PeriodNotClosedException.class)
    public ResponseEntity<ErrorResponse> handlePeriodNotClosed(PeriodNotClosedException e) {
        // 409 PERIOD_NOT_CLOSED — execute on an OPEN period (settlement-api.md error codes).
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("PERIOD_NOT_CLOSED", e.getMessage()));
    }

    @ExceptionHandler(PeriodNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePeriodNotFound(PeriodNotFoundException e) {
        // 404-over-403 — cross-tenant / absent period (M3).
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("SETTLEMENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED", e.getMessage()));
    }

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
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", e.getParameterName() + " is required"));
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

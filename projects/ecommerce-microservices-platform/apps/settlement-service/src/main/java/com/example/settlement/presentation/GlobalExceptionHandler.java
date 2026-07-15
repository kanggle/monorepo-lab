package com.example.settlement.presentation;

import com.example.settlement.application.exception.SellerScopeForbiddenException;
import com.example.settlement.domain.model.InvalidCommissionRateException;
import com.example.settlement.domain.period.PeriodAlreadyClosedException;
import com.example.settlement.domain.period.PeriodNotClosedException;
import com.example.settlement.domain.period.PeriodNotFoundException;
import com.example.settlement.domain.period.PeriodWindowInvalidException;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.AccessDeniedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps settlement exceptions to the settlement-api.md error contract:
 * {@code COMMISSION_RATE_INVALID} (422), {@code SETTLEMENT_NOT_FOUND} (404),
 * {@code ACCESS_DENIED} (403). Validation failures → 400 {@code VALIDATION_ERROR}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("Invalid input value");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Malformed request body"));
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

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", e.getParameterName() + " is required"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "The requested resource was not found"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "The requested resource was not found"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

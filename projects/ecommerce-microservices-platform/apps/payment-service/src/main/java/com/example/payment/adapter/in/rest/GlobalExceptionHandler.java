package com.example.payment.adapter.in.rest;

import com.example.web.dto.ErrorResponse;
import com.example.payment.application.exception.AmountMismatchException;
import com.example.payment.application.exception.PaymentAlreadyCompletedException;
import com.example.payment.application.exception.PgConfirmFailedException;
import com.example.payment.application.exception.PgGatewayUnavailableException;
import com.example.payment.application.exception.UnauthorizedPaymentAccessException;
import com.example.payment.domain.exception.InvalidPaymentException;
import com.example.payment.domain.exception.PaymentNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Malformed request body"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An internal server error occurred"));
    }
}

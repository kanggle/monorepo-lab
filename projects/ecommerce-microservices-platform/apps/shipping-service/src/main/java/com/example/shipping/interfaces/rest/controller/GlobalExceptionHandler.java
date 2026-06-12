package com.example.shipping.interfaces.rest.controller;

import com.example.web.dto.ErrorResponse;
import com.example.web.exception.AccessDeniedException;
import com.example.shipping.application.exception.UnauthorizedShippingAccessException;
import com.example.shipping.application.exception.WebhookSignatureException;
import com.example.shipping.domain.exception.InvalidShippingException;
import com.example.shipping.domain.exception.InvalidStatusTransitionException;
import com.example.shipping.domain.exception.ShippingNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException e) {
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_SHIPPING_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

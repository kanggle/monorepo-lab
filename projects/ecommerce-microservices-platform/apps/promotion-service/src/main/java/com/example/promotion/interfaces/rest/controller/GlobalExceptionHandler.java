package com.example.promotion.interfaces.rest.controller;

import com.example.promotion.application.exception.InvalidCouponStatusException;
import com.example.promotion.application.exception.InvalidPromotionStatusException;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.AccessDeniedException;
import com.example.promotion.domain.coupon.CouponAlreadyUsedException;
import com.example.promotion.domain.coupon.CouponExpiredException;
import com.example.promotion.domain.coupon.CouponNotFoundException;
import com.example.promotion.domain.coupon.CouponNotOwnedException;
import com.example.promotion.domain.coupon.CouponRestoreNotAllowedException;
import com.example.promotion.domain.promotion.CouponLimitExceededException;
import com.example.promotion.domain.promotion.PromotionAlreadyEndedException;
import com.example.promotion.domain.promotion.PromotionHasIssuedCouponsException;
import com.example.promotion.domain.promotion.PromotionNotActiveException;
import com.example.promotion.domain.promotion.PromotionNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;

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

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .findFirst()
                .orElse("Invalid input value");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Malformed request body"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException e) {
        if ("X-User-Role".equals(e.getHeaderName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.of("UNAUTHORIZED", "Missing authentication"));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHORIZED", "Missing authentication"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED", e.getMessage()));
    }

    @ExceptionHandler(PromotionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePromotionNotFound(PromotionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("PROMOTION_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(CouponNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCouponNotFound(CouponNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("COUPON_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(PromotionAlreadyEndedException.class)
    public ResponseEntity<ErrorResponse> handlePromotionAlreadyEnded(PromotionAlreadyEndedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("PROMOTION_ALREADY_ENDED", e.getMessage()));
    }

    @ExceptionHandler(PromotionHasIssuedCouponsException.class)
    public ResponseEntity<ErrorResponse> handlePromotionHasIssuedCoupons(PromotionHasIssuedCouponsException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("PROMOTION_HAS_ISSUED_COUPONS", e.getMessage()));
    }

    @ExceptionHandler(PromotionNotActiveException.class)
    public ResponseEntity<ErrorResponse> handlePromotionNotActive(PromotionNotActiveException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("PROMOTION_NOT_ACTIVE", e.getMessage()));
    }

    @ExceptionHandler(CouponLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleCouponLimitExceeded(CouponLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("COUPON_LIMIT_EXCEEDED", e.getMessage()));
    }

    @ExceptionHandler(CouponAlreadyUsedException.class)
    public ResponseEntity<ErrorResponse> handleCouponAlreadyUsed(CouponAlreadyUsedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("COUPON_ALREADY_USED", e.getMessage()));
    }

    @ExceptionHandler(CouponExpiredException.class)
    public ResponseEntity<ErrorResponse> handleCouponExpired(CouponExpiredException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("COUPON_EXPIRED", e.getMessage()));
    }

    @ExceptionHandler(CouponNotOwnedException.class)
    public ResponseEntity<ErrorResponse> handleCouponNotOwned(CouponNotOwnedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("COUPON_NOT_OWNED", e.getMessage()));
    }

    @ExceptionHandler(CouponRestoreNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleCouponRestoreNotAllowed(CouponRestoreNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("COUPON_RESTORE_NOT_ALLOWED", e.getMessage()));
    }

    @ExceptionHandler({InvalidPromotionStatusException.class, InvalidCouponStatusException.class})
    public ResponseEntity<ErrorResponse> handleInvalidStatus(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_PROMOTION_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ErrorResponse> handleDateTimeParse(DateTimeParseException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_PROMOTION_REQUEST", "Invalid date format: " + e.getParsedString()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_PROMOTION_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

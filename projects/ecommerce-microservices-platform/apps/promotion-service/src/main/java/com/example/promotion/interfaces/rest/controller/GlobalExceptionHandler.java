package com.example.promotion.interfaces.rest.controller;

import com.example.promotion.application.exception.IdempotencyKeyConflictException;
import com.example.promotion.application.exception.IdempotencyKeyRequiredException;
import com.example.promotion.application.exception.InvalidCouponStatusException;
import com.example.promotion.application.exception.InvalidPromotionStatusException;
import com.example.common.persistence.DataIntegrityViolations;
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
import com.example.web.exception.CommonGlobalExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;

/**
 * Non-domain arms (404/405/415/generic catch-all) come from
 * {@link CommonGlobalExceptionHandler} (ADR-MONO-058 § D2). {@link #handleValidation}
 * (no field-name prefix, "Invalid input value" fallback), {@link #handleMissingHeader}
 * (401 {@code UNAUTHORIZED}, not the shared handler's 400 {@code VALIDATION_ERROR}), and
 * {@link #handleIllegalArgument} (promotion-specific {@code INVALID_PROMOTION_REQUEST}
 * code) all diverge from the shared handler's defaults, so they stay local — as true
 * Java overrides (same name/signature/return type as the shared method, re-declaring
 * {@code @ExceptionHandler}), not differently-named methods, since Spring's resolver
 * throws {@code IllegalStateException: Ambiguous @ExceptionHandler} if two methods
 * (inherited + local) map the same exact exception type.
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

    @ExceptionHandler(IdempotencyKeyRequiredException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyRequired(IdempotencyKeyRequiredException e) {
        // 400 IDEMPOTENCY_KEY_REQUIRED — a keyless request is refused rather than
        // served non-idempotently (TASK-BE-536).
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("IDEMPOTENCY_KEY_REQUIRED", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyConflict(IdempotencyKeyConflictException e) {
        // 409 IDEMPOTENCY_KEY_CONFLICT — same key replayed with a different user
        // batch, or the loser of a concurrent same-key insert race (TASK-BE-536).
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("IDEMPOTENCY_KEY_CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ErrorResponse> handleDateTimeParse(DateTimeParseException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_PROMOTION_REQUEST", "Invalid date format: " + e.getParsedString()));
    }

    @Override
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_PROMOTION_REQUEST", e.getMessage()));
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

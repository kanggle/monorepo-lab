package com.example.fanplatform.membership.presentation.advice;

import com.example.fanplatform.membership.application.exception.IdempotencyKeyConflictException;
import com.example.fanplatform.membership.application.exception.MembershipNotFoundException;
import com.example.fanplatform.membership.application.exception.MembershipTierInvalidException;
import com.example.fanplatform.membership.application.exception.PaymentDeclinedException;
import com.example.fanplatform.membership.domain.membership.status.InvalidStateTransitionException;
import com.example.fanplatform.membership.presentation.dto.ApiErrorBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps domain/application exceptions to the platform error envelope. Status
 * conventions per {@code specs/contracts/http/membership-api.md}:
 *
 * <ul>
 *   <li>404 — MEMBERSHIP_NOT_FOUND (missing / cross-account / cross-tenant)</li>
 *   <li>409 — IDEMPOTENCY_KEY_CONFLICT / CONFLICT (optimistic lock)</li>
 *   <li>422 — PAYMENT_DECLINED / MEMBERSHIP_TIER_INVALID / MEMBERSHIP_STATE_INVALID / VALIDATION_ERROR</li>
 *   <li>400 — VALIDATION_ERROR (malformed body / missing Idempotency-Key)</li>
 * </ul>
 *
 * <p>Cross-cutting handlers (optimistic lock, integrity, validation,
 * missing-header, type-mismatch, illegal-argument/state, general) are inherited
 * from {@link AbstractDomainExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractDomainExceptionHandler {

    @ExceptionHandler(MembershipNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handleNotFound(MembershipNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("MEMBERSHIP_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    public ResponseEntity<ApiErrorBody> handlePaymentDeclined(PaymentDeclinedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("PAYMENT_DECLINED", e.getMessage()));
    }

    @ExceptionHandler(MembershipTierInvalidException.class)
    public ResponseEntity<ApiErrorBody> handleTierInvalid(MembershipTierInvalidException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("MEMBERSHIP_TIER_INVALID", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiErrorBody> handleIdempotencyConflict(IdempotencyKeyConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("IDEMPOTENCY_KEY_CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiErrorBody> handleInvalidTransition(InvalidStateTransitionException e) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", e.from().name());
        details.put("to", e.to().name());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("MEMBERSHIP_STATE_INVALID",
                        "Invalid membership status transition", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorBody> handleMalformed(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorBody.of("VALIDATION_ERROR", "Malformed request body"));
    }
}

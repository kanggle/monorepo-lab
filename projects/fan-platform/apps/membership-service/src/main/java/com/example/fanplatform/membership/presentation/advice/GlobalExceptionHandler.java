package com.example.fanplatform.membership.presentation.advice;

import com.example.fanplatform.membership.application.exception.BillingKeyEnrollmentNotFoundException;
import com.example.fanplatform.membership.application.exception.IdempotencyKeyConflictException;
import com.example.fanplatform.membership.application.exception.MembershipNotFoundException;
import com.example.fanplatform.membership.application.exception.MembershipNotRenewableException;
import com.example.fanplatform.membership.application.exception.MembershipTierInvalidException;
import com.example.fanplatform.membership.application.exception.PaymentDeclinedException;
import com.example.fanplatform.membership.domain.membership.status.InvalidStateTransitionException;
import com.example.fanplatform.membership.presentation.dto.ApiErrorBody;
import com.example.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * <p><strong>Envelope (ADR-MONO-058 § D2)</strong>: arms that carry no structured
 * context return {@code libs/java-web}'s shared {@link ErrorResponse}
 * ({@code {code, message, timestamp}}). The one arm with a documented {@code details}
 * payload — {@code MEMBERSHIP_STATE_INVALID} ({@code details.from}, {@code details.to}) —
 * returns {@link ApiErrorBody}, the {@code details}-carrying extension
 * {@code platform/error-handling.md § Error Response Format} explicitly permits.
 *
 * <p>Cross-cutting handlers are inherited from {@link AbstractDomainExceptionHandler}
 * (fan-platform policy) and, above it, {@code CommonGlobalExceptionHandler}
 * (framework arms: 400 / 404 / 405 / 409 / 415 / 500).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractDomainExceptionHandler {

    @ExceptionHandler(MembershipNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(MembershipNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("MEMBERSHIP_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(BillingKeyEnrollmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEnrollmentNotFound(BillingKeyEnrollmentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("BILLING_KEY_ENROLLMENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    public ResponseEntity<ErrorResponse> handlePaymentDeclined(PaymentDeclinedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("PAYMENT_DECLINED", e.getMessage()));
    }

    @ExceptionHandler(MembershipTierInvalidException.class)
    public ResponseEntity<ErrorResponse> handleTierInvalid(MembershipTierInvalidException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("MEMBERSHIP_TIER_INVALID", e.getMessage()));
    }

    @ExceptionHandler(MembershipNotRenewableException.class)
    public ResponseEntity<ErrorResponse> handleNotRenewable(MembershipNotRenewableException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("MEMBERSHIP_NOT_RENEWABLE", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyKeyConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("IDEMPOTENCY_KEY_CONFLICT", e.getMessage()));
    }

    /** Contract: {@code membership-api.md} — 422 with {@code details.from} / {@code details.to}. */
    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiErrorBody> handleInvalidTransition(InvalidStateTransitionException e) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", e.from().name());
        details.put("to", e.to().name());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.withDetails("MEMBERSHIP_STATE_INVALID",
                        "Invalid membership status transition", details));
    }

    // HttpMessageNotReadableException (malformed body) → 400 VALIDATION_ERROR
    // "Malformed request body" is inherited verbatim from CommonGlobalExceptionHandler;
    // the local copy this class carried was byte-identical (ADR-MONO-058 § D2).
}

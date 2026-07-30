package com.example.fanplatform.membership.presentation.advice;

import com.example.common.persistence.DataIntegrityViolations;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * fan-platform-owned exception-handling policy for membership-service.
 * Service-specific domain handlers live in {@link GlobalExceptionHandler}.
 *
 * <p><strong>ADR-MONO-058 § D2</strong>: the framework/non-domain arms
 * (404 {@code NoResourceFound}/{@code NoHandlerFound}, 405 {@code MethodNotSupported}
 * incl. the RFC 7231 {@code Allow} header, 415 {@code MediaTypeNotSupported},
 * 400 malformed-body / missing-header / missing-parameter, 409
 * {@code ObjectOptimisticLockingFailureException}, {@code @Valid} violations, and the
 * catch-all 500) are now inherited from {@code libs/java-web-servlet}'s
 * {@link CommonGlobalExceptionHandler} instead of being hand-copied here. Only the
 * four arms below are genuinely fan-platform policy and stay service-side.
 *
 * <p>The missing-{@code Idempotency-Key} guard this class used to carry explicitly is
 * the base's {@code MissingRequestHeaderException} arm — same 400 status, same
 * {@code "Missing required header: <name>"} message ({@code membership-api.md}:
 * "400 VALIDATION_ERROR | … missing `Idempotency-Key` on subscribe").
 */
@Slf4j
abstract class AbstractDomainExceptionHandler extends CommonGlobalExceptionHandler {

    /**
     * fan-platform publishes <strong>422</strong> for {@code @Valid} constraint
     * violations and for {@code IllegalArgumentException} at the controller boundary
     * ({@code membership-api.md} — "422 VALIDATION_ERROR | constraint violation
     * (`@Valid`, e.g. `planMonths < 1`)"), where the shared default is 400. One override
     * moves both inherited arms; see
     * {@link CommonGlobalExceptionHandler#validationFailureStatus()}.
     */
    @Override
    protected HttpStatus validationFailureStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    /**
     * JPA's own {@link OptimisticLockException} — distinct from Spring's
     * {@code ObjectOptimisticLockingFailureException}, which the shared base already
     * maps to the same 409. Declaring both here would be an
     * <em>Ambiguous @ExceptionHandler</em> boot failure, so only the jakarta variant
     * stays local.
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleJpaOptimisticLock(OptimisticLockException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONFLICT", "Concurrent modification detected. Please retry."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
        if (DataIntegrityViolations.isUniqueViolation(e)) {
            // Unique violation = client-visible conflict → 409 (the registry catch-all).
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of("CONFLICT", "Data integrity violation"));
        }
        // FK / NOT NULL / CHECK violations are SERVER defects, not client conflicts.
        // Kept as 500 so they stay loud in logs + alerting (TASK-MONO-450 / TASK-BE-542).
        log.error("Non-unique data integrity violation", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    /**
     * Not covered by the shared base — {@code membership-api.md} publishes
     * "400 VALIDATION_ERROR | … type mismatch", so without this arm the case would fall
     * through to the catch-all and regress to 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Invalid parameter: " + e.getName()));
    }

    /** Registered as {@code ILLEGAL_STATE} 422 in {@code platform/error-handling.md} § General. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        log.warn("illegal state at controller boundary", e);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("ILLEGAL_STATE", e.getMessage()));
    }
}

package com.example.scmplatform.logistics.adapter.inbound.web.advice;

import com.example.scmplatform.logistics.domain.error.CarrierUnroutableException;
import com.example.scmplatform.logistics.domain.error.DispatchNotFoundException;
import com.example.scmplatform.logistics.domain.error.IllegalDispatchTransitionException;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps domain exceptions to the scm platform error envelope
 * {@code { code, message, timestamp }}.
 * Logistics codes: {@code DISPATCH_NOT_FOUND} (404), {@code CARRIER_UNROUTABLE} (422),
 * {@code DISPATCH_ALREADY_COMPLETED} (409) — registered in {@code platform/error-handling.md}
 * + {@code rules/domains/scm.md}.
 *
 * <p><strong>ADR-MONO-058 § D2 (TASK-SCM-BE-055)</strong>: the framework/non-domain arms
 * (404 {@code NoResourceFound}/{@code NoHandlerFound}, 405 {@code MethodNotSupported}
 * incl. the RFC 7231 {@code Allow} header, 415 {@code MediaTypeNotSupported}, 400
 * malformed-body / missing-header / missing-parameter, {@code @Valid} violations, and the
 * catch-all 500) are inherited from {@code libs/java-web-servlet}'s
 * {@link CommonGlobalExceptionHandler} instead of being hand-copied here.
 *
 * <p>Adopting the shared {@link ErrorResponse} also adds the {@code timestamp} field this
 * service's advice previously omitted, making the controller-advice envelope identical to
 * the one {@code HttpErrorResponseWriter} already emits from the security layer, and
 * bringing both onto {@code platform/error-handling.md § Error Response Format} ("the
 * three fields above must always be present").
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * scm publishes <strong>422</strong> for {@code @Valid} constraint violations and for
     * {@code IllegalArgumentException} at the controller boundary — the convention its
     * three sibling services already follow — where the shared default is 400. One
     * override moves both inherited arms; see
     * {@link CommonGlobalExceptionHandler#validationFailureStatus()}.
     */
    @Override
    protected HttpStatus validationFailureStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    @ExceptionHandler(DispatchNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDispatchNotFound(DispatchNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("DISPATCH_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(IllegalDispatchTransitionException.class)
    public ResponseEntity<ErrorResponse> handleIllegalTransition(IllegalDispatchTransitionException e) {
        // A completed dispatch cannot be re-driven into a non-terminal state (S1).
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("DISPATCH_ALREADY_COMPLETED", e.getMessage()));
    }

    @ExceptionHandler(CarrierUnroutableException.class)
    public ResponseEntity<ErrorResponse> handleCarrierUnroutable(CarrierUnroutableException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("CARRIER_UNROUTABLE", e.getMessage()));
    }

    /**
     * Spring's {@link ObjectOptimisticLockingFailureException}.
     * <strong>Overrides</strong> the shared base rather than inheriting it: the base
     * answers 409 {@code CONFLICT}, while scm answers 409 {@code CONCURRENT_MODIFICATION}
     * (retry-OK signal). Because the base's arm is registered for the <em>more specific</em>
     * subclass, inheriting it unchanged would have shadowed
     * {@link #handleDataAccessOptimisticLock} for the commonest concrete type and silently
     * renamed a published error code.
     */
    @Override
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        log.warn("Concurrent modification on dispatch: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONCURRENT_MODIFICATION",
                        "The dispatch was modified concurrently; retry the request"));
    }

    /**
     * The broader Spring-Data {@link OptimisticLockingFailureException} (superclass of the
     * arm above), kept for non-JPA/`@Version` collision paths. Distinct method name is
     * required — same-name-different-type would not be an override and two handlers for
     * one type is an "Ambiguous @ExceptionHandler" boot failure.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessOptimisticLock(OptimisticLockingFailureException e) {
        log.warn("Concurrent modification on dispatch: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONCURRENT_MODIFICATION",
                        "The dispatch was modified concurrently; retry the request"));
    }

    /**
     * Not covered by {@link CommonGlobalExceptionHandler} — without this arm a non-UUID
     * path variable would fall through to the catch-all and regress the documented 400
     * into a 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Invalid parameter: " + e.getName()));
    }

    /**
     * <strong>Overrides</strong> the shared base only to keep the diagnostic log line —
     * the status and body come straight from {@code super}, i.e. 422 via
     * {@link #validationFailureStatus()}.
     */
    @Override
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Rejecting request with 422 VALIDATION_ERROR: {}", e.getMessage());
        return super.handleIllegalArgument(e);
    }
}

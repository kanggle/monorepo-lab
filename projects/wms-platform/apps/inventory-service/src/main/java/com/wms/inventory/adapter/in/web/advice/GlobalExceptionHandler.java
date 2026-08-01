package com.wms.inventory.adapter.in.web.advice;

import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import com.wms.inventory.domain.exception.AdjustmentNotFoundException;
import com.wms.inventory.domain.exception.AdjustmentReasonRequiredException;
import com.wms.inventory.domain.exception.DuplicateRequestException;
import com.wms.inventory.domain.exception.InventoryDomainException;
import com.wms.inventory.domain.exception.InventoryNotFoundException;
import com.wms.inventory.domain.exception.InventoryValidationException;
import com.wms.inventory.domain.exception.ReservationNotFoundException;
import com.wms.inventory.domain.exception.TransferNotFoundException;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps inventory domain exceptions to the flat {@code {code, message, timestamp}}
 * error body declared in {@code specs/contracts/http/inventory-service-api.md}
 * § Error Envelope.
 *
 * <p>Domain → HTTP status table is reproduced here as the controlling rules.
 * Most {@link InventoryDomainException}s are business-rule violations → 422 (the
 * default); only lookups (404), duplicates (409) and field-validation (400) map
 * elsewhere.
 *
 * <p><strong>ADR-MONO-058 § D2 (TASK-BE-567)</strong>: the non-domain / framework
 * arms (404 {@code NoResourceFound} / {@code NoHandlerFound}, 405
 * {@code MethodNotSupported} incl. the RFC 7231 {@code Allow} header, 415
 * {@code MediaTypeNotSupported}, 400 {@code @Valid} / {@code IllegalArgument} /
 * malformed-body / missing-header / missing-parameter, and the catch-all 500) are
 * inherited from {@code libs/java-web-servlet}'s
 * {@link CommonGlobalExceptionHandler} instead of being hand-copied here. That is
 * wire-preserving for this service because its former {@code ApiErrorEnvelope}
 * was a component-for-component duplicate of {@code libs/java-web}'s
 * {@link ErrorResponse} ({@code code}, {@code message}, pre-formatted ISO-8601
 * {@code timestamp} string) — unlike {@code master-service} / {@code admin-service},
 * whose envelope nests everything under a top-level {@code error} key and which
 * therefore compose the shared handler instead of extending it.
 *
 * <p>Only the arms below are genuinely inventory-owned policy and stay service-side.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    /**
     * Domain exception → HTTP status override. The default is 422 (business-rule violation —
     * covers {@code TransferSameLocation}, {@code StateTransitionInvalid},
     * {@code ReservationQuantityMismatch}, {@code InsufficientStock}, {@code MasterRefInactive},
     * etc.); this table lists only the exceptions that map elsewhere. Keyed on the exact concrete
     * class. Replaces the prior one-{@code @ExceptionHandler}-per-type boilerplate.
     */
    private static final Map<Class<? extends InventoryDomainException>, HttpStatus> DOMAIN_STATUS = Map.of(
            InventoryNotFoundException.class, HttpStatus.NOT_FOUND,
            ReservationNotFoundException.class, HttpStatus.NOT_FOUND,
            AdjustmentNotFoundException.class, HttpStatus.NOT_FOUND,
            TransferNotFoundException.class, HttpStatus.NOT_FOUND,
            DuplicateRequestException.class, HttpStatus.CONFLICT,
            AdjustmentReasonRequiredException.class, HttpStatus.BAD_REQUEST,
            InventoryValidationException.class, HttpStatus.BAD_REQUEST);

    @ExceptionHandler(InventoryDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(InventoryDomainException e) {
        HttpStatus status = DOMAIN_STATUS.getOrDefault(e.getClass(), HttpStatus.UNPROCESSABLE_ENTITY);
        return body(status, e.errorCode(), e.getMessage());
    }

    /**
     * Optimistic-lock collision. Declared on Spring's <em>broader</em>
     * {@link OptimisticLockingFailureException} — the shared base only covers the
     * narrower {@link ObjectOptimisticLockingFailureException} subtype, so this arm
     * is not a duplicate of it and stays service-local.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConflict(OptimisticLockingFailureException e) {
        return body(HttpStatus.CONFLICT, "CONFLICT", "Optimistic lock conflict — retry with fresh state");
    }

    /**
     * Routes the narrower JPA-raised subtype back to {@link #handleConflict} so that both
     * subtypes answer with inventory's published message rather than the shared base's
     * different wording. Overriding (same signature) rather than declaring a second
     * {@code @ExceptionHandler} for the same type is required — the latter is a boot-time
     * {@code Ambiguous @ExceptionHandler method} failure.
     */
    @Override
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return handleConflict(e);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(RuntimeException e) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Insufficient privileges for this operation");
    }

    /**
     * Path-variable / query-parameter type mismatch (e.g. a non-UUID {@code {id}}).
     * Not covered by the shared base — without this arm it would fall through to the
     * inherited catch-all and regress the contract's documented 400 into a 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
    }

    private static ResponseEntity<ErrorResponse> body(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message));
    }
}

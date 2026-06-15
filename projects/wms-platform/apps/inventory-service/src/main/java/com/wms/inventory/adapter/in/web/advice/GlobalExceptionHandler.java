package com.wms.inventory.adapter.in.web.advice;

import com.wms.inventory.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.inventory.domain.exception.AdjustmentNotFoundException;
import com.wms.inventory.domain.exception.AdjustmentReasonRequiredException;
import com.wms.inventory.domain.exception.DuplicateRequestException;
import com.wms.inventory.domain.exception.InventoryDomainException;
import com.wms.inventory.domain.exception.InventoryNotFoundException;
import com.wms.inventory.domain.exception.InventoryValidationException;
import com.wms.inventory.domain.exception.ReservationNotFoundException;
import com.wms.inventory.domain.exception.TransferNotFoundException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps domain + framework exceptions to the {@link ApiErrorEnvelope} shape
 * declared in {@code inventory-service-api.md} § Error Envelope.
 *
 * <p>Domain → HTTP status table is reproduced here as the controlling rules.
 * Most {@link InventoryDomainException}s are business-rule violations → 422 (the
 * default); only lookups (404), duplicates (409) and field-validation (400) map
 * elsewhere. Unknown exceptions surface as {@code 500 INTERNAL_ERROR} with the
 * cause logged but not returned to the caller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
    public ResponseEntity<ApiErrorEnvelope> handleDomain(InventoryDomainException e) {
        HttpStatus status = DOMAIN_STATUS.getOrDefault(e.getClass(), HttpStatus.UNPROCESSABLE_ENTITY);
        return body(status, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorEnvelope> handleConflict(OptimisticLockingFailureException e) {
        return body(HttpStatus.CONFLICT, "CONFLICT", "Optimistic lock conflict — retry with fresh state");
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiErrorEnvelope> handleForbidden(RuntimeException e) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Insufficient privileges for this operation");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class})
    public ResponseEntity<ApiErrorEnvelope> handleBadInput(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorEnvelope> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Internal server error");
    }

    private static ResponseEntity<ApiErrorEnvelope> body(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorEnvelope.of(code, message));
    }
}

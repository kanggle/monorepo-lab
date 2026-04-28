package com.wms.inventory.adapter.in.web.advice;

import com.wms.inventory.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.inventory.domain.exception.AdjustmentNotFoundException;
import com.wms.inventory.domain.exception.AdjustmentReasonRequiredException;
import com.wms.inventory.domain.exception.DuplicateRequestException;
import com.wms.inventory.domain.exception.InsufficientStockException;
import com.wms.inventory.domain.exception.InventoryDomainException;
import com.wms.inventory.domain.exception.InventoryNotFoundException;
import com.wms.inventory.domain.exception.InventoryValidationException;
import com.wms.inventory.domain.exception.MasterRefInactiveException;
import com.wms.inventory.domain.exception.ReservationNotFoundException;
import com.wms.inventory.domain.exception.ReservationQuantityMismatchException;
import com.wms.inventory.domain.exception.StateTransitionInvalidException;
import com.wms.inventory.domain.exception.TransferNotFoundException;
import com.wms.inventory.domain.exception.TransferSameLocationException;
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
 * Unknown exceptions surface as {@code 500 INTERNAL_ERROR} with the cause
 * logged but not returned to the caller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({InventoryNotFoundException.class, ReservationNotFoundException.class,
            AdjustmentNotFoundException.class, TransferNotFoundException.class})
    public ResponseEntity<ApiErrorEnvelope> handleNotFound(InventoryDomainException e) {
        return body(HttpStatus.NOT_FOUND, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(TransferSameLocationException.class)
    public ResponseEntity<ApiErrorEnvelope> handleTransferSameLocation(TransferSameLocationException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(AdjustmentReasonRequiredException.class)
    public ResponseEntity<ApiErrorEnvelope> handleReasonRequired(AdjustmentReasonRequiredException e) {
        return body(HttpStatus.BAD_REQUEST, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(StateTransitionInvalidException.class)
    public ResponseEntity<ApiErrorEnvelope> handleStateTransition(StateTransitionInvalidException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(ReservationQuantityMismatchException.class)
    public ResponseEntity<ApiErrorEnvelope> handleQtyMismatch(ReservationQuantityMismatchException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDuplicateRequest(DuplicateRequestException e) {
        return body(HttpStatus.CONFLICT, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiErrorEnvelope> handleInsufficientStock(InsufficientStockException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(MasterRefInactiveException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMasterRefInactive(MasterRefInactiveException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(InventoryValidationException.class)
    public ResponseEntity<ApiErrorEnvelope> handleValidation(InventoryValidationException e) {
        return body(HttpStatus.BAD_REQUEST, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(InventoryDomainException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDomain(InventoryDomainException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.errorCode(), e.getMessage());
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

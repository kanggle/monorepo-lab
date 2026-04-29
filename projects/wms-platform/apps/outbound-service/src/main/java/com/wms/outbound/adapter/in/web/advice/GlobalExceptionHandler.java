package com.wms.outbound.adapter.in.web.advice;

import com.wms.outbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.outbound.domain.exception.OrderAlreadyShippedException;
import com.wms.outbound.domain.exception.OrderNoDuplicateException;
import com.wms.outbound.domain.exception.OrderNotFoundException;
import com.wms.outbound.domain.exception.OutboundDomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
 * Maps framework + outbound domain exceptions to the {@link ApiErrorEnvelope}
 * shape declared in {@code platform/error-handling.md}.
 *
 * <p>Domain exceptions extend {@link OutboundDomainException} and each
 * override {@link OutboundDomainException#errorCode()} with the
 * contract-defined string from {@code outbound-service-api.md} § Error Codes.
 * This handler reads {@code exception.errorCode()} so the envelope's
 * {@code code} is always the granular contract-defined string.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- 404 -------------------------------------------------------------

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleOrderNotFound(OrderNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.errorCode(), e.getMessage());
    }

    // ---- 409 -------------------------------------------------------------

    @ExceptionHandler(OrderNoDuplicateException.class)
    public ResponseEntity<ApiErrorEnvelope> handleOrderNoDuplicate(OrderNoDuplicateException e) {
        return body(HttpStatus.CONFLICT, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorEnvelope> handleConflict(OptimisticLockingFailureException e) {
        return body(HttpStatus.CONFLICT, "CONFLICT",
                "Optimistic lock conflict — retry with fresh state");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorEnvelope> handleIntegrity(DataIntegrityViolationException e) {
        // Unique constraint violations on orderNo etc. surface as 409 CONFLICT.
        log.debug("data integrity violation: {}", e.getMessage());
        return body(HttpStatus.CONFLICT, "CONFLICT",
                "Resource already exists or violates a constraint");
    }

    // ---- 422 — domain rule violations -----------------------------------

    @ExceptionHandler(OrderAlreadyShippedException.class)
    public ResponseEntity<ApiErrorEnvelope> handleAlreadyShipped(OrderAlreadyShippedException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.errorCode(), e.getMessage());
    }

    /** Catch-all for outbound domain exceptions → 422 with the granular code. */
    @ExceptionHandler(OutboundDomainException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDomain(OutboundDomainException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.errorCode(), e.getMessage());
    }

    // ---- 403 / 400 / 500 -------------------------------------------------

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

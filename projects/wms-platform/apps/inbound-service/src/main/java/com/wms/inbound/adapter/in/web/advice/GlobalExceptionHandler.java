package com.wms.inbound.adapter.in.web.advice;

import com.wms.inbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.inbound.domain.exception.AsnNoDuplicateException;
import com.wms.inbound.domain.exception.AsnNotFoundException;
import com.wms.inbound.domain.exception.InboundDomainException;
import com.wms.inbound.domain.exception.InspectionNotFoundException;
import com.wms.inbound.domain.exception.PutawayInstructionNotFoundException;
import com.wms.inbound.domain.exception.PutawayLineNotFoundException;
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
 * Maps framework + domain exceptions to the {@link ApiErrorEnvelope}
 * shape declared in {@code platform/error-handling.md}.
 *
 * <p>Domain exceptions extend {@link InboundDomainException} and each
 * override {@link InboundDomainException#errorCode()} with the contract-defined
 * string from {@code specs/contracts/http/inbound-service-api.md} §"Error Codes".
 * This handler calls {@code exception.errorCode()} directly so that the
 * {@code ApiErrorEnvelope.code} field is always the granular, stable code —
 * no more generic {@code UNPROCESSABLE_ENTITY}.
 *
 * <p>Unknown exceptions surface as {@code 500 INTERNAL_ERROR} with the cause
 * logged but not returned to the caller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------------------------
    // 404 Not Found — typed handlers so errorCode() is resolved per exception
    // -------------------------------------------------------------------------

    @ExceptionHandler(AsnNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleAsnNotFound(AsnNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(InspectionNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleInspectionNotFound(InspectionNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(PutawayInstructionNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handlePutawayInstructionNotFound(
            PutawayInstructionNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(PutawayLineNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handlePutawayLineNotFound(PutawayLineNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.errorCode(), e.getMessage());
    }

    // -------------------------------------------------------------------------
    // 409 Conflict
    // -------------------------------------------------------------------------

    @ExceptionHandler(AsnNoDuplicateException.class)
    public ResponseEntity<ApiErrorEnvelope> handleAsnDuplicate(AsnNoDuplicateException e) {
        return body(HttpStatus.CONFLICT, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorEnvelope> handleConflict(OptimisticLockingFailureException e) {
        return body(HttpStatus.CONFLICT, "CONFLICT", "Optimistic lock conflict — retry with fresh state");
    }

    // -------------------------------------------------------------------------
    // 422 Unprocessable Entity — all remaining domain exceptions
    // -------------------------------------------------------------------------

    @ExceptionHandler(InboundDomainException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDomainException(InboundDomainException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.errorCode(), e.getMessage());
    }

    // -------------------------------------------------------------------------
    // 403 Forbidden
    // -------------------------------------------------------------------------

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiErrorEnvelope> handleForbidden(RuntimeException e) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Insufficient privileges for this operation");
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request
    // -------------------------------------------------------------------------

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class})
    public ResponseEntity<ApiErrorEnvelope> handleBadInput(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
    }

    // -------------------------------------------------------------------------
    // 500 fallback
    // -------------------------------------------------------------------------

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

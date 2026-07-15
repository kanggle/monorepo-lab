package com.wms.inbound.adapter.in.web.advice;

import com.wms.inbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.inbound.domain.exception.AsnNoDuplicateException;
import com.wms.inbound.domain.exception.AsnNotFoundException;
import com.wms.inbound.domain.exception.InboundDomainException;
import com.wms.inbound.domain.exception.InspectionNotFoundException;
import com.wms.inbound.domain.exception.PutawayInstructionNotFoundException;
import com.wms.inbound.domain.exception.PutawayLineNotFoundException;
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
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps framework + domain exceptions to the {@link ApiErrorEnvelope}
 * shape declared in {@code platform/error-handling.md}.
 *
 * <p>Domain exceptions extend {@link InboundDomainException} and each
 * override {@link InboundDomainException#errorCode()} with the contract-defined
 * string from {@code specs/contracts/http/inbound-service-api.md} §"Error Codes".
 * This handler calls {@code exception.errorCode()} directly so that the
 * {@code ApiErrorEnvelope.code} field is always the granular, stable code.
 *
 * <p>Unknown exceptions surface as {@code 500 INTERNAL_ERROR} with the cause
 * logged but not returned to the caller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Domain exception → HTTP status override. Most {@link InboundDomainException}s are
     * business-rule violations → 422 (the default in {@link #handleDomainException}); this table
     * lists only the exceptions that map elsewhere (404 lookups, 409 duplicates). Keyed on the
     * exact concrete class. Replaces the prior one-{@code @ExceptionHandler}-per-type boilerplate.
     */
    private static final Map<Class<? extends InboundDomainException>, HttpStatus> DOMAIN_STATUS = Map.of(
            AsnNotFoundException.class, HttpStatus.NOT_FOUND,
            InspectionNotFoundException.class, HttpStatus.NOT_FOUND,
            PutawayInstructionNotFoundException.class, HttpStatus.NOT_FOUND,
            PutawayLineNotFoundException.class, HttpStatus.NOT_FOUND,
            AsnNoDuplicateException.class, HttpStatus.CONFLICT);

    @ExceptionHandler(InboundDomainException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDomainException(InboundDomainException e) {
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

    /**
     * Defense-in-depth (TASK-MONO-420): a request to a path this service does
     * not serve raises {@link NoResourceFoundException} (static resource lookup) or
     * {@link NoHandlerFoundException} (no matching handler). Without these handlers
     * they fall through to {@link #handleUnknown} → 500, masking a mis-route as
     * a service fault. Map them to a clean 404 so mis-routes degrade honestly.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleNoResource(NoResourceFoundException e) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleNoHandlerFound(NoHandlerFoundException e) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
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

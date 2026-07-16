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
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * Wrong HTTP method on a matched path (TASK-MONO-421) — Spring throws
     * {@link HttpRequestMethodNotSupportedException}. Without a dedicated handler the catch-all
     * {@link #handleUnknown} swallows it into a 500; semantically it is a client error (405).
     * Emits the RFC 7231 §6.5.5 {@code Allow} header listing the supported methods.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            builder.allow(supported.toArray(new HttpMethod[0]));
        }
        return builder.body(ApiErrorEnvelope.of("METHOD_NOT_ALLOWED",
                "HTTP method not supported for this endpoint"));
    }

    /**
     * Unsupported request {@code Content-Type} on a matched path (TASK-MONO-421) — Spring throws
     * {@link HttpMediaTypeNotSupportedException}. Same catch-all-swallow-into-500 defect as
     * {@link #handleMethodNotSupported}; semantically a 415.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Request Content-Type is not supported by this endpoint");
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

package com.wms.outbound.adapter.in.web.advice;

import com.wms.outbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.outbound.domain.exception.ExternalServiceUnavailableException;
import com.wms.outbound.domain.exception.OrderNoDuplicateException;
import com.wms.outbound.domain.exception.OrderNotFoundException;
import com.wms.outbound.domain.exception.OutboundDomainException;
import com.wms.outbound.domain.exception.PackingUnitNotFoundException;
import com.wms.outbound.domain.exception.PickingRequestNotFoundException;
import com.wms.outbound.domain.exception.ShipmentNotFoundException;
import com.wms.outbound.domain.exception.TenantScopeDeniedException;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * Domain exception → HTTP status override. Most {@link OutboundDomainException}s are
     * business-rule violations → 422 (the default in {@link #handleDomain}, covering e.g.
     * {@code OrderAlreadyShippedException}); this table lists only the exceptions that map
     * elsewhere (404 lookups, 409 duplicates). {@link ExternalServiceUnavailableException}
     * is handled separately (503, with vendor logging). Keyed on the exact concrete class.
     */
    private static final Map<Class<? extends OutboundDomainException>, HttpStatus> DOMAIN_STATUS = Map.of(
            OrderNotFoundException.class, HttpStatus.NOT_FOUND,
            PickingRequestNotFoundException.class, HttpStatus.NOT_FOUND,
            PackingUnitNotFoundException.class, HttpStatus.NOT_FOUND,
            ShipmentNotFoundException.class, HttpStatus.NOT_FOUND,
            OrderNoDuplicateException.class, HttpStatus.CONFLICT,
            // Cross-tenant access denial (TASK-MONO-304 / ADR-MONO-022 § D9) → 403.
            TenantScopeDeniedException.class, HttpStatus.FORBIDDEN);

    @ExceptionHandler(OutboundDomainException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDomain(OutboundDomainException e) {
        HttpStatus status = DOMAIN_STATUS.getOrDefault(e.getClass(), HttpStatus.UNPROCESSABLE_ENTITY);
        return body(status, e.errorCode(), e.getMessage());
    }

    /**
     * External vendor (TMS, ERP webhook out, etc.) unreachable / circuit-open
     * / retry-exhausted. Mapped to 503 per {@code platform/error-handling.md}
     * (registered globally for {@code integration-heavy} trait). Specific subtype
     * handler — takes precedence over {@link #handleDomain} (which would default it to 422).
     *
     * <p>The {@code RetryTmsNotificationService} catches this internally for
     * the manual-retry endpoint (returning 200 with the failed snapshot per
     * {@code outbound-service-api.md} §4.3), so this handler is the
     * defensive fallback for any other path that lets it escape.
     */
    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<ApiErrorEnvelope> handleExternalUnavailable(ExternalServiceUnavailableException e) {
        log.warn("external_service_unavailable vendor={} reason={}", e.getVendor(), e.getMessage());
        return body(HttpStatus.SERVICE_UNAVAILABLE, e.errorCode(), e.getMessage());
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

package com.wms.master.adapter.in.web.advice;

import com.wms.master.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.master.domain.exception.BarcodeDuplicateException;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.DataScopeForbiddenException;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.LocationCodeDuplicateException;
import com.wms.master.domain.exception.LocationNotFoundException;
import com.wms.master.domain.exception.LotNoDuplicateException;
import com.wms.master.domain.exception.LotNotFoundException;
import com.wms.master.domain.exception.MasterDomainException;
import com.wms.master.domain.exception.PartnerCodeDuplicateException;
import com.wms.master.domain.exception.PartnerNotFoundException;
import com.wms.master.domain.exception.ReferenceIntegrityViolationException;
import com.wms.master.domain.exception.SkuCodeDuplicateException;
import com.wms.master.domain.exception.SkuNotFoundException;
import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.exception.WarehouseCodeDuplicateException;
import com.wms.master.domain.exception.WarehouseNotFoundException;
import com.wms.master.domain.exception.ZoneCodeDuplicateException;
import com.wms.master.domain.exception.ZoneNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Concrete domain exception → HTTP status. Replaces one-{@code @ExceptionHandler}-per-type
     * boilerplate with a single explicit table (the only thing those handlers ever varied was
     * the status; the body is always {@code ApiErrorEnvelope.of(code, message)} via {@link #build}).
     * Keyed on the exact concrete class — every entry below is a direct {@link MasterDomainException}
     * subclass. Unmapped domain exceptions fall through to 500 (see {@link #handleDomain}).
     */
    private static final Map<Class<? extends MasterDomainException>, HttpStatus> DOMAIN_STATUS = Map.ofEntries(
            Map.entry(WarehouseNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(ZoneNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(LocationNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(SkuNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(PartnerNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(LotNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(WarehouseCodeDuplicateException.class, HttpStatus.CONFLICT),
            Map.entry(ZoneCodeDuplicateException.class, HttpStatus.CONFLICT),
            Map.entry(LocationCodeDuplicateException.class, HttpStatus.CONFLICT),
            Map.entry(SkuCodeDuplicateException.class, HttpStatus.CONFLICT),
            Map.entry(BarcodeDuplicateException.class, HttpStatus.CONFLICT),
            Map.entry(PartnerCodeDuplicateException.class, HttpStatus.CONFLICT),
            Map.entry(LotNoDuplicateException.class, HttpStatus.CONFLICT),
            Map.entry(ConcurrencyConflictException.class, HttpStatus.CONFLICT),
            // REFERENCE_INTEGRITY_VIOLATION → 409 (master-service-api.md): cross-aggregate orphan
            // risk, distinct from STATE_TRANSITION_INVALID 422 (single-aggregate invariants).
            Map.entry(ReferenceIntegrityViolationException.class, HttpStatus.CONFLICT),
            // STATE_TRANSITION_INVALID → 422 (platform/error-handling.md): unprocessable business
            // rule violation.
            Map.entry(InvalidStateTransitionException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ImmutableFieldException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ValidationException.class, HttpStatus.BAD_REQUEST),
            // TASK-MONO-215 (ADR-MONO-025 § 3.3 step 2): data-scoped operator targeted a warehouse
            // outside its data_scope set → 403 DATA_SCOPE_FORBIDDEN (ABAC data visibility, distinct
            // from RBAC FORBIDDEN and tenant TENANT_FORBIDDEN).
            Map.entry(DataScopeForbiddenException.class, HttpStatus.FORBIDDEN));

    @ExceptionHandler(MasterDomainException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDomain(MasterDomainException ex) {
        HttpStatus status = DOMAIN_STATUS.get(ex.getClass());
        if (status == null) {
            log.warn("Unmapped domain exception: {} — {}", ex.getCode(), ex.getMessage());
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return build(status, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorEnvelope> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        Map<String, Object> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                details.put(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiErrorEnvelope.of("VALIDATION_ERROR", message, details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMalformed(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorEnvelope.of("VALIDATION_ERROR", "Malformed request body"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorEnvelope.of("VALIDATION_ERROR",
                        "Missing required parameter: " + ex.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorEnvelope> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorEnvelope.of("VALIDATION_ERROR",
                        "Invalid value for parameter: " + ex.getName()));
    }

    /**
     * {@code @PreAuthorize} on application-service methods throws
     * {@link AccessDeniedException} (or its Spring Security 6 subclass
     * {@code AuthorizationDeniedException}) that bubbles past the Spring
     * Security filter chain, because the failure originates inside the
     * controller/service call. Without this explicit handler the generic
     * {@link #handleUnexpected} fallback maps it to 500; integration tests
     * that exercise role enforcement (TASK-BE-017) then see a 500 instead of
     * the contracted 403. Mirror the {@code accessDeniedHandler} in
     * {@link com.wms.master.config.SecurityConfig} for consistency.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorEnvelope> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorEnvelope.of("FORBIDDEN",
                        "Insufficient privileges for this operation"));
    }

    /**
     * {@link AuthenticationCredentialsNotFoundException} surfaces when
     * method-security gates a call but the SecurityContext is empty — e.g., a
     * mis-configured request that slipped past the authentication filter.
     * Map to 401 per the platform error table rather than letting the generic
     * handler downgrade it to 500.
     */
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMissingCredentials(
            AuthenticationCredentialsNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorEnvelope.of("UNAUTHORIZED", "Authentication required"));
    }

    /**
     * Defense-in-depth (TASK-MONO-162): a request to a path this service does
     * not serve raises {@link NoResourceFoundException}. Without this handler it
     * falls through to {@link #handleUnexpected} → 500, which a caller (e.g. the
     * console-bff leg classifier) reads as {@code DOWNSTREAM_ERROR/degraded}
     * rather than the truthful "not found" — masking a mis-route as a service
     * fault. Map it to a clean 404 so future mis-routes degrade honestly.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorEnvelope.of("NOT_FOUND", "Resource not found"));
    }

    /**
     * Wrong HTTP method on a matched path (TASK-MONO-421) — Spring throws
     * {@link HttpRequestMethodNotSupportedException}. Without a dedicated handler the catch-all
     * {@link #handleUnexpected} swallows it into a 500; semantically it is a client error (405).
     * Emits the RFC 7231 §6.5.5 {@code Allow} header listing the supported methods.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
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
    public ResponseEntity<ApiErrorEnvelope> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiErrorEnvelope.of("UNSUPPORTED_MEDIA_TYPE",
                        "Request Content-Type is not supported by this endpoint"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorEnvelope> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorEnvelope.of("INTERNAL_ERROR", "Internal server error"));
    }

    private static ResponseEntity<ApiErrorEnvelope> build(HttpStatus status, MasterDomainException ex) {
        return ResponseEntity.status(status)
                .body(ApiErrorEnvelope.of(ex.getCode(), ex.getMessage()));
    }
}

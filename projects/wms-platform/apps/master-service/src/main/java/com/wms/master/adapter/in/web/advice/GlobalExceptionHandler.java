package com.wms.master.adapter.in.web.advice;

import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Maps master domain + framework exceptions to the nested
 * {@code {"error": {code, message, timestamp, …}}} envelope declared in
 * {@code specs/contracts/http/master-service-api.md} § Error Envelope and pinned by
 * {@code src/test/resources/contracts/http/error-envelope.schema.json}.
 *
 * <p><strong>ADR-MONO-058 § D2 (TASK-BE-567) — composition, not inheritance.</strong>
 * The non-domain / framework arms below no longer carry hand-written
 * status/code/message decisions: each one delegates to {@code libs/java-web-servlet}'s
 * {@link CommonGlobalExceptionHandler} through {@link #SHARED} and then re-wraps the
 * result into master's own {@link ApiErrorEnvelope}, preserving both the status and any
 * response headers (notably RFC 7231 {@code Allow} on 405).
 *
 * <p>This service deliberately does <em>not</em> {@code extend}
 * {@link CommonGlobalExceptionHandler}, unlike its {@code inbound} / {@code inventory} /
 * {@code outbound} siblings. Those three publish the shared library's flat
 * {@code {code, message, timestamp}} body, so inheriting its
 * {@code ResponseEntity<ErrorResponse>} arms is wire-preserving for them. Master's
 * published body nests everything under a top-level {@code error} key, and the shared
 * arms' return type is invariantly bound to {@code ErrorResponse} — an override
 * returning {@code ResponseEntity<ApiErrorEnvelope>} does not compile, and adding a
 * second, differently-named {@code @ExceptionHandler} for an already-inherited type is a
 * boot-time {@code Ambiguous @ExceptionHandler method} failure. Composition is therefore
 * the only shape that removes the duplicated mapping logic without changing the
 * published JSON shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * The shared non-domain exception → (status, code, message, headers) decision table
     * (ADR-MONO-058 § D2). A plain object, not a Spring bean and not itself annotated
     * {@code @ControllerAdvice}, so none of its {@code @ExceptionHandler} methods are
     * registered — it is consulted only through the delegating arms below.
     */
    private static final CommonGlobalExceptionHandler SHARED = new CommonGlobalExceptionHandler() {};

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
        return rewrap(SHARED.handleMalformedRequest(ex));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMissingParam(MissingServletRequestParameterException ex) {
        return rewrap(SHARED.handleMissingParam(ex));
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
        return rewrap(SHARED.handleNoResourceFound(ex));
    }

    /**
     * Wrong HTTP method on a matched path (TASK-MONO-421) — Spring throws
     * {@link HttpRequestMethodNotSupportedException}. Without a dedicated handler the catch-all
     * {@link #handleUnexpected} swallows it into a 500; semantically it is a client error (405).
     * The RFC 7231 §6.5.5 {@code Allow} header is produced by the shared arm and carried across
     * by {@link #rewrap}.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return rewrap(SHARED.handleMethodNotSupported(ex));
    }

    /**
     * Unsupported request {@code Content-Type} on a matched path (TASK-MONO-421) — Spring throws
     * {@link HttpMediaTypeNotSupportedException}. Same catch-all-swallow-into-500 defect as
     * {@link #handleMethodNotSupported}; semantically a 415.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return rewrap(SHARED.handleMediaTypeNotSupported(ex));
    }

    /** Catch-all. The shared arm logs the cause at ERROR with its stack trace. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorEnvelope> handleUnexpected(Exception ex) {
        return rewrap(SHARED.handleGeneral(ex));
    }

    private static ResponseEntity<ApiErrorEnvelope> build(HttpStatus status, MasterDomainException ex) {
        return ResponseEntity.status(status)
                .body(ApiErrorEnvelope.of(ex.getCode(), ex.getMessage()));
    }

    /**
     * Re-wraps a shared-handler response into master's nested envelope. Status and every
     * response header (notably {@code Allow} on 405) are carried across verbatim; only the
     * body is re-shaped from the shared flat {@link ErrorResponse} into
     * {@link ApiErrorEnvelope}, whose factory stamps a fresh {@code timestamp}.
     */
    private static ResponseEntity<ApiErrorEnvelope> rewrap(ResponseEntity<ErrorResponse> shared) {
        ErrorResponse b = shared.getBody();
        return ResponseEntity.status(shared.getStatusCode())
                .headers(shared.getHeaders())
                .body(ApiErrorEnvelope.of(b.code(), b.message()));
    }
}

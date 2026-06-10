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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WarehouseNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleNotFound(WarehouseNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(WarehouseCodeDuplicateException.class)
    public ResponseEntity<ApiErrorEnvelope> handleCodeDuplicate(WarehouseCodeDuplicateException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(ZoneNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleZoneNotFound(ZoneNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(ZoneCodeDuplicateException.class)
    public ResponseEntity<ApiErrorEnvelope> handleZoneCodeDuplicate(ZoneCodeDuplicateException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(LocationNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleLocationNotFound(LocationNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(LocationCodeDuplicateException.class)
    public ResponseEntity<ApiErrorEnvelope> handleLocationCodeDuplicate(LocationCodeDuplicateException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(SkuNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleSkuNotFound(SkuNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(SkuCodeDuplicateException.class)
    public ResponseEntity<ApiErrorEnvelope> handleSkuCodeDuplicate(SkuCodeDuplicateException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(BarcodeDuplicateException.class)
    public ResponseEntity<ApiErrorEnvelope> handleBarcodeDuplicate(BarcodeDuplicateException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(PartnerNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handlePartnerNotFound(PartnerNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(PartnerCodeDuplicateException.class)
    public ResponseEntity<ApiErrorEnvelope> handlePartnerCodeDuplicate(PartnerCodeDuplicateException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(LotNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleLotNotFound(LotNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(LotNoDuplicateException.class)
    public ResponseEntity<ApiErrorEnvelope> handleLotNoDuplicate(LotNoDuplicateException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(ConcurrencyConflictException.class)
    public ResponseEntity<ApiErrorEnvelope> handleConflict(ConcurrencyConflictException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiErrorEnvelope> handleInvalidTransition(InvalidStateTransitionException ex) {
        // Per platform/error-handling.md, STATE_TRANSITION_INVALID is an
        // unprocessable business rule violation → 422 Unprocessable Entity.
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(ReferenceIntegrityViolationException.class)
    public ResponseEntity<ApiErrorEnvelope> handleReferenceIntegrity(ReferenceIntegrityViolationException ex) {
        // Per specs/contracts/http/master-service-api.md, REFERENCE_INTEGRITY_VIOLATION
        // maps to 409 CONFLICT (distinct from STATE_TRANSITION_INVALID 422 — the latter
        // covers single-aggregate invariants; this covers cross-aggregate orphan risk).
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(ImmutableFieldException.class)
    public ResponseEntity<ApiErrorEnvelope> handleImmutableField(ImmutableFieldException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDomainValidation(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(MasterDomainException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDomain(MasterDomainException ex) {
        log.warn("Unmapped domain exception: {} — {}", ex.getCode(), ex.getMessage());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex);
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
     * TASK-MONO-215 (ADR-MONO-025 § 3.3 step 2): a deliberately data-scoped
     * operator targeted a warehouse outside its {@code data_scope} set → 403
     * {@code DATA_SCOPE_FORBIDDEN} (distinct from the RBAC {@code FORBIDDEN} and
     * the tenant {@code TENANT_FORBIDDEN}; ABAC data visibility, not privilege).
     */
    @ExceptionHandler(DataScopeForbiddenException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDataScopeForbidden(DataScopeForbiddenException ex) {
        return build(HttpStatus.FORBIDDEN, ex);
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

package com.wms.admin.api.advice;

import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import com.wms.admin.api.dto.ApiErrorEnvelope;
import com.wms.admin.domain.error.AdminDomainException;
import com.wms.admin.domain.error.AlertNotFoundException;
import com.wms.admin.domain.error.AssignmentNotFoundException;
import com.wms.admin.domain.error.RoleBuiltinImmutableException;
import com.wms.admin.domain.error.RoleCodeDuplicateException;
import com.wms.admin.domain.error.RoleInUseException;
import com.wms.admin.domain.error.RoleNotFoundException;
import com.wms.admin.domain.error.SettingImmutableFieldException;
import com.wms.admin.domain.error.SettingNotFoundException;
import com.wms.admin.domain.error.SettingValidationErrorException;
import com.wms.admin.domain.error.StateTransitionInvalidException;
import com.wms.admin.domain.error.UserEmailDuplicateException;
import com.wms.admin.domain.error.UserHasActiveAssignmentsException;
import com.wms.admin.domain.error.UserNotFoundException;
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
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps {@link AdminDomainException} subtypes to HTTP status codes per
 * {@code platform/error-handling.md § Admin}, wrapped in the nested
 * {@code {"error": {code, message, timestamp, …}}} envelope declared in
 * {@code specs/contracts/http/admin-service-api.md} § Error Envelope.
 *
 * <p><strong>ADR-MONO-058 § D2 (TASK-BE-567) — composition, not inheritance.</strong>
 * The non-domain / framework arms below no longer carry hand-written
 * status/code/message decisions: each one delegates to {@code libs/java-web-servlet}'s
 * {@link CommonGlobalExceptionHandler} through {@link #SHARED} and then re-wraps the
 * result into admin's own {@link ApiErrorEnvelope}, preserving both the status and any
 * response headers (notably RFC 7231 {@code Allow} on 405).
 *
 * <p>This service deliberately does <em>not</em> {@code extend}
 * {@link CommonGlobalExceptionHandler}, unlike its {@code inbound} / {@code inventory} /
 * {@code outbound} siblings. Those three publish the shared library's flat
 * {@code {code, message, timestamp}} body, so inheriting its
 * {@code ResponseEntity<ErrorResponse>} arms is wire-preserving for them. Admin's
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
     * Concrete domain exception → HTTP status (per {@code platform/error-handling.md § Admin}).
     * Replaces one-{@code @ExceptionHandler}-per-type boilerplate with a single explicit table;
     * the body is always {@code ApiErrorEnvelope.of(code, message)} via {@link #build}. Keyed on
     * the exact concrete class (each a direct {@link AdminDomainException} subclass). Unmapped
     * domain exceptions fall through to 500 + a warn (see {@link #handleDomain}).
     */
    private static final Map<Class<? extends AdminDomainException>, HttpStatus> DOMAIN_STATUS = Map.ofEntries(
            Map.entry(UserNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(RoleNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(AssignmentNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(SettingNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(AlertNotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(UserEmailDuplicateException.class, HttpStatus.CONFLICT),
            Map.entry(RoleCodeDuplicateException.class, HttpStatus.CONFLICT),
            Map.entry(UserHasActiveAssignmentsException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(RoleInUseException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(RoleBuiltinImmutableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(SettingImmutableFieldException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(StateTransitionInvalidException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(SettingValidationErrorException.class, HttpStatus.BAD_REQUEST));

    @ExceptionHandler(AdminDomainException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDomain(AdminDomainException ex) {
        HttpStatus status = DOMAIN_STATUS.get(ex.getClass());
        if (status == null) {
            log.warn("Unmapped admin domain exception: {} — {}", ex.getCode(), ex.getMessage());
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorEnvelope> handleIllegalArgument(IllegalArgumentException ex) {
        return rewrap(SHARED.handleIllegalArgument(ex));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorEnvelope> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorEnvelope.of("FORBIDDEN",
                        "Insufficient privileges for this operation"));
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMissingCredentials(
            AuthenticationCredentialsNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorEnvelope.of("UNAUTHORIZED", "Authentication required"));
    }

    /**
     * Defense-in-depth (TASK-MONO-420): a request to a path this service does
     * not serve raises {@link NoResourceFoundException} (static resource lookup) or
     * {@link NoHandlerFoundException} (no matching handler). Without these handlers
     * they fall through to {@link #handleUnexpected} → 500, masking a mis-route as
     * a service fault. Map them to a clean 404 so mis-routes degrade honestly.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleNoResource(NoResourceFoundException ex) {
        return rewrap(SHARED.handleNoResourceFound(ex));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleNoHandlerFound(NoHandlerFoundException ex) {
        return rewrap(SHARED.handleNoHandlerFound(ex));
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

    private static ResponseEntity<ApiErrorEnvelope> build(HttpStatus status, AdminDomainException ex) {
        return ResponseEntity.status(status)
                .body(ApiErrorEnvelope.of(ex.getCode(), ex.getMessage()));
    }

    /**
     * Re-wraps a shared-handler response into admin's nested envelope. Status and every
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

package com.wms.admin.api.advice;

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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps {@link AdminDomainException} subtypes to HTTP status codes per
 * {@code platform/error-handling.md § Admin}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorEnvelope> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorEnvelope.of("VALIDATION_ERROR", ex.getMessage()));
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
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorEnvelope.of("NOT_FOUND", "Resource not found"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleNoHandlerFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorEnvelope.of("NOT_FOUND", "Resource not found"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorEnvelope> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorEnvelope.of("INTERNAL_ERROR", "Internal server error"));
    }

    private static ResponseEntity<ApiErrorEnvelope> build(HttpStatus status, AdminDomainException ex) {
        return ResponseEntity.status(status)
                .body(ApiErrorEnvelope.of(ex.getCode(), ex.getMessage()));
    }
}

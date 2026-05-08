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

/**
 * Maps {@link AdminDomainException} subtypes to HTTP status codes per
 * {@code platform/error-handling.md § Admin}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ----- 404 ----------------------------------------------------------------

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleUserNotFound(UserNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleRoleNotFound(RoleNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(AssignmentNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleAssignmentNotFound(AssignmentNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(SettingNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleSettingNotFound(SettingNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(AlertNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleAlertNotFound(AlertNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    // ----- 409 ----------------------------------------------------------------

    @ExceptionHandler(UserEmailDuplicateException.class)
    public ResponseEntity<ApiErrorEnvelope> handleUserEmailDuplicate(UserEmailDuplicateException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(RoleCodeDuplicateException.class)
    public ResponseEntity<ApiErrorEnvelope> handleRoleCodeDuplicate(RoleCodeDuplicateException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    // ----- 422 ----------------------------------------------------------------

    @ExceptionHandler(UserHasActiveAssignmentsException.class)
    public ResponseEntity<ApiErrorEnvelope> handleUserHasActive(UserHasActiveAssignmentsException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(RoleInUseException.class)
    public ResponseEntity<ApiErrorEnvelope> handleRoleInUse(RoleInUseException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(RoleBuiltinImmutableException.class)
    public ResponseEntity<ApiErrorEnvelope> handleRoleBuiltin(RoleBuiltinImmutableException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(SettingImmutableFieldException.class)
    public ResponseEntity<ApiErrorEnvelope> handleSettingImmutable(SettingImmutableFieldException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(StateTransitionInvalidException.class)
    public ResponseEntity<ApiErrorEnvelope> handleStateTransition(StateTransitionInvalidException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    // ----- 400 ----------------------------------------------------------------

    @ExceptionHandler(SettingValidationErrorException.class)
    public ResponseEntity<ApiErrorEnvelope> handleSettingValidation(SettingValidationErrorException ex) {
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(AdminDomainException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDomain(AdminDomainException ex) {
        log.warn("Unmapped admin domain exception: {} — {}", ex.getCode(), ex.getMessage());
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

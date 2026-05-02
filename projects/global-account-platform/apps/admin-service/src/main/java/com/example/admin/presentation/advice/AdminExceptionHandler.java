package com.example.admin.presentation.advice;

import com.example.admin.application.exception.AuditFailureException;
import com.example.admin.application.exception.CurrentPasswordMismatchException;
import com.example.admin.application.exception.PasswordPolicyViolationException;
import com.example.admin.application.exception.BatchSizeExceededException;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.EnrollmentRequiredException;
import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.exception.InvalidBootstrapTokenException;
import com.example.admin.application.exception.InvalidCredentialsException;
import com.example.admin.application.exception.InvalidLoginRequestException;
import com.example.admin.application.exception.InvalidRecoveryCodeException;
import com.example.admin.application.exception.InvalidRefreshTokenException;
import com.example.admin.application.exception.InvalidTwoFaCodeException;
import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.RefreshTokenReuseDetectedException;
import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.application.exception.SelfSuspendForbiddenException;
import com.example.admin.application.exception.StateTransitionInvalidException;
import com.example.admin.application.exception.TenantAlreadyExistsException;
import com.example.admin.application.exception.TenantIdReservedException;
import com.example.admin.application.exception.TenantNotFoundException;
import com.example.admin.application.exception.TokenRevokedException;
import com.example.admin.application.exception.TotpNotEnrolledException;
import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.exception.PermissionDeniedException;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.presentation.dto.EnrollmentRequiredResponse;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class AdminExceptionHandler extends CommonGlobalExceptionHandler {

    @ExceptionHandler(ReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> handleReasonRequired(ReasonRequiredException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("REASON_REQUIRED", e.getMessage()));
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePermission(PermissionDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("PERMISSION_DENIED", e.getMessage()));
    }

    // TASK-BE-249 — tenant scope violation
    @ExceptionHandler(TenantScopeDeniedException.class)
    public ResponseEntity<ErrorResponse> handleTenantScopeDenied(TenantScopeDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("TENANT_SCOPE_DENIED", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("PERMISSION_DENIED", "Operator role insufficient"));
    }

    @ExceptionHandler(OperatorUnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(OperatorUnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("TOKEN_INVALID", e.getMessage()));
    }

    @ExceptionHandler(InvalidBootstrapTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBootstrap(InvalidBootstrapTokenException e) {
        log.debug("bootstrap token rejected: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_BOOTSTRAP_TOKEN",
                        "Bootstrap token is missing, expired, or has been consumed"));
    }

    @ExceptionHandler(InvalidTwoFaCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalid2Fa(InvalidTwoFaCodeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_2FA_CODE", "TOTP code is invalid"));
    }

    @ExceptionHandler(TotpNotEnrolledException.class)
    public ResponseEntity<ErrorResponse> handleTotpNotEnrolled(TotpNotEnrolledException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("TOTP_NOT_ENROLLED",
                        "TOTP enrollment is required before recovery-code regeneration"));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_CREDENTIALS", "Operator credentials are invalid"));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefresh(InvalidRefreshTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_REFRESH_TOKEN", "Refresh token is invalid"));
    }

    @ExceptionHandler(RefreshTokenReuseDetectedException.class)
    public ResponseEntity<ErrorResponse> handleRefreshReuse(RefreshTokenReuseDetectedException e) {
        log.warn("refresh token reuse detected: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("REFRESH_TOKEN_REUSE_DETECTED",
                        "Refresh token chain has been invalidated"));
    }

    @ExceptionHandler(TokenRevokedException.class)
    public ResponseEntity<ErrorResponse> handleTokenRevoked(TokenRevokedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("TOKEN_REVOKED", "Operator token has been revoked"));
    }

    @ExceptionHandler(InvalidRecoveryCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRecovery(InvalidRecoveryCodeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_RECOVERY_CODE", "Recovery code is invalid"));
    }

    @ExceptionHandler(InvalidLoginRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLoginRequest(InvalidLoginRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(EnrollmentRequiredException.class)
    public ResponseEntity<EnrollmentRequiredResponse> handleEnrollmentRequired(
            EnrollmentRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new EnrollmentRequiredResponse(
                        "ENROLLMENT_REQUIRED",
                        "Operator must complete 2FA enrollment before login",
                        e.getBootstrapToken(),
                        e.getExpiresInSeconds()));
    }

    @ExceptionHandler(DownstreamFailureException.class)
    public ResponseEntity<ErrorResponse> handleDownstream(DownstreamFailureException e) {
        log.warn("downstream failure: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("DOWNSTREAM_ERROR", "Downstream service unavailable"));
    }

    /**
     * Resilience4j CircuitBreaker in OPEN state rejects calls with
     * {@link io.github.resilience4j.circuitbreaker.CallNotPermittedException}
     * (a subclass of RuntimeException, not DownstreamFailureException).
     * Surface as 503 SERVICE_UNAVAILABLE with a distinct code so operators and
     * dashboards can distinguish "circuit open" from raw downstream failures.
     */
    @ExceptionHandler(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitOpen(
            io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
        log.warn("circuit breaker OPEN, rejecting call: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("CIRCUIT_OPEN", "Downstream circuit is open; try again shortly"));
    }

    @ExceptionHandler(AuditFailureException.class)
    public ResponseEntity<ErrorResponse> handleAuditFailure(AuditFailureException e) {
        log.error("fail-closed: audit write failed, command aborted", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("AUDIT_FAILURE", "Audit write failed; command aborted"));
    }

    @ExceptionHandler(BatchSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleBatchSizeExceeded(BatchSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("BATCH_SIZE_EXCEEDED", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyKeyConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("IDEMPOTENCY_KEY_CONFLICT", e.getMessage()));
    }

    // TASK-BE-083 — operator management errors.
    @ExceptionHandler(OperatorEmailConflictException.class)
    public ResponseEntity<ErrorResponse> handleOperatorEmailConflict(OperatorEmailConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("OPERATOR_EMAIL_CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(OperatorNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOperatorNotFound(OperatorNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("OPERATOR_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoleNotFound(RoleNotFoundException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("ROLE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(SelfSuspendForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleSelfSuspend(SelfSuspendForbiddenException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("SELF_SUSPEND_FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(StateTransitionInvalidException.class)
    public ResponseEntity<ErrorResponse> handleStateTransition(StateTransitionInvalidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("STATE_TRANSITION_INVALID", e.getMessage()));
    }

    // TASK-BE-250 — tenant lifecycle errors
    @ExceptionHandler(TenantIdReservedException.class)
    public ResponseEntity<ErrorResponse> handleTenantIdReserved(TenantIdReservedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("TENANT_ID_RESERVED", e.getMessage()));
    }

    @ExceptionHandler(TenantAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleTenantAlreadyExists(TenantAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("TENANT_ALREADY_EXISTS", e.getMessage()));
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFound(TenantNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("TENANT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(CurrentPasswordMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCurrentPasswordMismatch(CurrentPasswordMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("CURRENT_PASSWORD_MISMATCH", e.getMessage()));
    }

    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<ErrorResponse> handlePasswordPolicy(PasswordPolicyViolationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("PASSWORD_POLICY_VIOLATION", e.getMessage()));
    }

    /**
     * Overrides the base handler to apply X-Operator-Reason-specific error code when
     * that header is missing, as required by admin-service's reason audit policy.
     */
    @Override
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        String header = e.getHeaderName();
        if ("X-Operator-Reason".equalsIgnoreCase(header)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of("REASON_REQUIRED", "X-Operator-Reason header is required"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Missing required header: " + header));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            jakarta.validation.ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Invalid parameter: " + e.getName()));
    }
}

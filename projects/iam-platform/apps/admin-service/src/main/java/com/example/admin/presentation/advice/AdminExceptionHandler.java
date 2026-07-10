package com.example.admin.presentation.advice;

import com.example.admin.application.exception.AccessConditionUnmetException;
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
import com.example.admin.application.exception.InvalidRequestException;
import com.example.admin.application.exception.InvalidRefreshTokenException;
import com.example.admin.application.exception.InvalidTokenExchangeRequestException;
import com.example.admin.application.exception.InvalidTwoFaCodeException;
import com.example.admin.application.exception.OperatorAccountNotFoundException;
import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.RefreshTokenReuseDetectedException;
import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.application.exception.SelfSuspendForbiddenException;
import com.example.admin.application.exception.StateTransitionInvalidException;
import com.example.admin.application.exception.SubscriptionAlreadyExistsException;
import com.example.admin.application.exception.SubscriptionNotFoundException;
import com.example.admin.application.exception.SubscriptionTransitionInvalidException;
import com.example.admin.application.exception.TenantAlreadyExistsException;
import com.example.admin.application.exception.TenantIdReservedException;
import com.example.admin.application.exception.TenantNotFoundException;
import com.example.admin.application.exception.TokenRevokedException;
import com.example.admin.application.exception.TotpNotEnrolledException;
import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.exception.PermissionDeniedException;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.exception.TenantScopeMismatchException;
import com.example.admin.application.exception.AssignmentNotFoundException;
import com.example.admin.application.exception.AssignmentAlreadyExistsException;
import com.example.admin.application.exception.RoleGrantForbiddenException;
import com.example.admin.presentation.dto.EnrollmentRequiredResponse;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    // TASK-BE-339 — org_scope management: path tenantId != active X-Tenant-Id.
    @ExceptionHandler(TenantScopeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTenantScopeMismatch(TenantScopeMismatchException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("TENANT_SCOPE_MISMATCH", e.getMessage()));
    }

    // TASK-BE-339 — org_scope management: no assignment row for (operator, tenant).
    @ExceptionHandler(AssignmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAssignmentNotFound(AssignmentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("ASSIGNMENT_NOT_FOUND", e.getMessage()));
    }

    // TASK-BE-347 (ADR-MONO-024 D3-i) — assign surface: duplicate (operator, tenant) row.
    @ExceptionHandler(AssignmentAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAssignmentAlreadyExists(AssignmentAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("ASSIGNMENT_ALREADY_EXISTS", e.getMessage()));
    }

    // TASK-BE-347 (ADR-MONO-024 D3) — grant-menu no-escalation violation.
    @ExceptionHandler(RoleGrantForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleRoleGrantForbidden(RoleGrantForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ROLE_GRANT_FORBIDDEN", e.getMessage()));
    }

    // TASK-BE-351 (ADR-MONO-026, axis ② 2단계) — admin mutation passed RBAC but
    // the request source IP is outside the configured allowlist (the SOURCE_IP
    // access condition, the 4th authorization gate). Restriction-only: only
    // raised after the permission check granted.
    @ExceptionHandler(AccessConditionUnmetException.class)
    public ResponseEntity<ErrorResponse> handleAccessConditionUnmet(AccessConditionUnmetException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_CONDITION_UNMET", e.getMessage()));
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

    // TASK-BE-298 / ADR-MONO-014 — RFC 8693 protocol-shape error
    // (grant_type / subject_token_type mismatch). admin-api.md
    // §token-exchange error table: 400 BAD_REQUEST. (A subject-token /
    // mapping failure is SubjectTokenInvalidException, which extends
    // OperatorUnauthorizedException → 401 TOKEN_INVALID, handled above.)
    @ExceptionHandler(InvalidTokenExchangeRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTokenExchange(
            InvalidTokenExchangeRequestException e) {
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

    // TASK-MONO-334 (ADR-MONO-035 amendment) — POST /api/admin/operators targets an
    // email with no signed-up account in the tenant. An unprocessable precondition
    // (mirrors the 422 IDENTITY_LINK family), DISTINCT from the 409 email-conflict:
    // 409 = the email is already an operator; 422 = the email is not yet an account.
    @ExceptionHandler(OperatorAccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOperatorAccountNotFound(OperatorAccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("OPERATOR_ACCOUNT_NOT_FOUND", e.getMessage()));
    }

    // TASK-BE-373 (ADR-MONO-034 U3) — operator↔identity link errors.
    // Email-match is necessary-not-sufficient: a mismatch is a semantically-invalid
    // request (422, mirroring BATCH_SIZE_EXCEEDED's 422 family for unprocessable input).
    @ExceptionHandler(com.example.admin.application.exception.IdentityLinkEmailMismatchException.class)
    public ResponseEntity<ErrorResponse> handleIdentityLinkEmailMismatch(
            com.example.admin.application.exception.IdentityLinkEmailMismatchException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("IDENTITY_LINK_EMAIL_MISMATCH", e.getMessage()));
    }

    // Fail-closed: the account resolves to no central identity (200 + null). Not a
    // downstream failure (that is 503 DOWNSTREAM_ERROR) — an unprocessable link target.
    @ExceptionHandler(com.example.admin.application.exception.AccountIdentityUnresolvableException.class)
    public ResponseEntity<ErrorResponse> handleAccountIdentityUnresolvable(
            com.example.admin.application.exception.AccountIdentityUnresolvableException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("ACCOUNT_IDENTITY_UNRESOLVABLE", e.getMessage()));
    }

    // Already linked to a DIFFERENT identity → conflict (mirror OPERATOR_EMAIL_CONFLICT 409).
    @ExceptionHandler(com.example.admin.application.exception.OperatorAlreadyLinkedException.class)
    public ResponseEntity<ErrorResponse> handleOperatorAlreadyLinked(
            com.example.admin.application.exception.OperatorAlreadyLinkedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("OPERATOR_ALREADY_LINKED", e.getMessage()));
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

    @ExceptionHandler(com.example.admin.application.exception.SelfProfileUpdateForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleSelfProfileUpdate(
            com.example.admin.application.exception.SelfProfileUpdateForbiddenException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH", e.getMessage()));
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

    // TASK-BE-343 (ADR-MONO-023 D3) — subscription management delegation errors
    // (account-service 404/409 surfaced unchanged to the operator).
    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSubscriptionNotFound(SubscriptionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("SUBSCRIPTION_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(SubscriptionAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleSubscriptionAlreadyExists(SubscriptionAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("SUBSCRIPTION_ALREADY_EXISTS", e.getMessage()));
    }

    @ExceptionHandler(SubscriptionTransitionInvalidException.class)
    public ResponseEntity<ErrorResponse> handleSubscriptionTransitionInvalid(
            SubscriptionTransitionInvalidException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("SUBSCRIPTION_TRANSITION_INVALID", e.getMessage()));
    }

    // TASK-BE-477 (ADR-MONO-045) — cross-org partnership management errors
    // (admin-api.md § Partnership Management error table).
    @ExceptionHandler(com.example.admin.application.exception.PartnershipScopeDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePartnershipScopeDenied(
            com.example.admin.application.exception.PartnershipScopeDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("PARTNERSHIP_SCOPE_DENIED", e.getMessage()));
    }

    @ExceptionHandler(com.example.admin.application.exception.PartnershipScopeInvalidException.class)
    public ResponseEntity<ErrorResponse> handlePartnershipScopeInvalid(
            com.example.admin.application.exception.PartnershipScopeInvalidException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("PARTNERSHIP_SCOPE_INVALID", e.getMessage()));
    }

    @ExceptionHandler(com.example.admin.application.exception.ParticipantNotOwnOperatorException.class)
    public ResponseEntity<ErrorResponse> handleParticipantNotOwnOperator(
            com.example.admin.application.exception.ParticipantNotOwnOperatorException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("PARTICIPANT_NOT_OWN_OPERATOR", e.getMessage()));
    }

    @ExceptionHandler(com.example.admin.application.exception.ParticipantScopeExceedsDelegationException.class)
    public ResponseEntity<ErrorResponse> handleParticipantScopeExceeds(
            com.example.admin.application.exception.ParticipantScopeExceedsDelegationException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("PARTICIPANT_SCOPE_EXCEEDS_DELEGATION", e.getMessage()));
    }

    @ExceptionHandler(com.example.admin.application.exception.PartnershipNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePartnershipNotFound(
            com.example.admin.application.exception.PartnershipNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("PARTNERSHIP_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(com.example.admin.application.exception.ParticipantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleParticipantNotFound(
            com.example.admin.application.exception.ParticipantNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("PARTICIPANT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(com.example.admin.application.exception.PartnershipAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handlePartnershipAlreadyExists(
            com.example.admin.application.exception.PartnershipAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("PARTNERSHIP_ALREADY_EXISTS", e.getMessage()));
    }

    @ExceptionHandler(com.example.admin.application.exception.PartnershipTransitionInvalidException.class)
    public ResponseEntity<ErrorResponse> handlePartnershipTransitionInvalid(
            com.example.admin.application.exception.PartnershipTransitionInvalidException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("PARTNERSHIP_TRANSITION_INVALID", e.getMessage()));
    }

    // TASK-BE-492 (ADR-MONO-047 D5) — org-node tree errors.
    //
    // Cross-scope is 404, NOT 403: a 403 would confirm that a node outside the actor's
    // subtree exists (same enumeration-safety rule as the cross-tenant account path).
    @ExceptionHandler(com.example.admin.application.exception.OrgNodeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrgNodeNotFound(
            com.example.admin.application.exception.OrgNodeNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("ORG_NODE_NOT_FOUND", e.getMessage()));
    }

    // 403 (not 404) is right here: the actor demonstrably administers the node, so its
    // existence is not a secret — it just may not edit its OWN ceiling (self-escalation).
    @ExceptionHandler(com.example.admin.application.exception.OrgNodeSelfCeilingDeniedException.class)
    public ResponseEntity<ErrorResponse> handleOrgNodeSelfCeilingDenied(
            com.example.admin.application.exception.OrgNodeSelfCeilingDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ORG_NODE_SELF_CEILING_DENIED", e.getMessage()));
    }

    @ExceptionHandler(com.example.admin.application.exception.OrgAdminGrantOutOfCeilingException.class)
    public ResponseEntity<ErrorResponse> handleOrgAdminGrantOutOfCeiling(
            com.example.admin.application.exception.OrgAdminGrantOutOfCeilingException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("ORG_ADMIN_GRANT_OUT_OF_CEILING", e.getMessage()));
    }

    // The account-service org-node authority's 422, passed through with its own code
    // (ORG_NODE_CYCLE / ORG_NODE_DEPTH_EXCEEDED / ORG_NODE_CEILING_NOT_SUBSET /
    // ORG_NODE_NOT_EMPTY). admin-service does not re-implement those invariants.
    @ExceptionHandler(com.example.admin.application.exception.OrgNodeInvariantViolationException.class)
    public ResponseEntity<ErrorResponse> handleOrgNodeInvariantViolation(
            com.example.admin.application.exception.OrgNodeInvariantViolationException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(e.getCode(), e.getMessage()));
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

    // TASK-BE-306 — request body shape / structural validation failed
    // (PATCH /api/admin/operators/me/profile + future surfaces that want the
    // canonical INVALID_REQUEST code instead of the generic VALIDATION_ERROR).
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_REQUEST", e.getMessage()));
    }

    // TASK-BE-306 — optimistic-lock race on admin_operators.version. The
    // parent CommonGlobalExceptionHandler maps this to 409 CONFLICT, but
    // admin-api.md § PATCH /api/admin/operators/me/profile mandates the
    // canonical OPTIMISTIC_LOCK_CONFLICT error code — override here so the
    // contract is honored uniformly across the admin-service surface.
    @Override
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("OPTIMISTIC_LOCK_CONFLICT",
                        "Concurrent modification detected. Please retry."));
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

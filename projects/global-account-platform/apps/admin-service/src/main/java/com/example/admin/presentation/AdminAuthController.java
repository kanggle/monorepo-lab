package com.example.admin.presentation;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.AdminLoginService;
import com.example.admin.application.AdminLogoutService;
import com.example.admin.application.AdminRefreshTokenService;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.Outcome;
import com.example.admin.application.TotpEnrollmentService;
import com.example.admin.application.exception.EnrollmentRequiredException;
import com.example.admin.application.exception.InvalidBootstrapTokenException;
import com.example.admin.application.exception.InvalidCredentialsException;
import com.example.admin.application.exception.InvalidLoginRequestException;
import com.example.admin.application.exception.InvalidRecoveryCodeException;
import com.example.admin.application.exception.InvalidRefreshTokenException;
import com.example.admin.application.exception.InvalidTwoFaCodeException;
import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.exception.RefreshTokenReuseDetectedException;
import com.example.admin.application.exception.TotpNotEnrolledException;
import com.example.admin.infrastructure.security.BootstrapContext;
import com.example.admin.infrastructure.security.BootstrapTokenService;
import com.example.admin.infrastructure.security.OperatorAuthenticationFilter;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.dto.AdminLoginRequest;
import com.example.admin.presentation.dto.AdminLoginResponse;
import com.example.admin.presentation.dto.AdminLogoutRequest;
import com.example.admin.presentation.dto.AdminRefreshRequest;
import com.example.admin.presentation.dto.AdminRefreshResponse;
import com.example.admin.presentation.dto.RegenerateRecoveryCodesResponse;
import com.example.admin.presentation.dto.TotpEnrollResponse;
import com.example.admin.presentation.dto.TotpVerifyRequest;
import com.example.admin.presentation.dto.TotpVerifyResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * admin-service authentication endpoints that sit BEFORE the operator JWT
 * is issued. See admin-api.md §Authentication Exceptions.
 *
 * <p>029-2 shipped the 2FA enroll/verify endpoints (bootstrap-token-auth);
 * 029-3 adds {@code /login} which accepts password + optional 2FA and mints
 * the operator JWT (or a bootstrap token when enrollment is outstanding).
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final TotpEnrollmentService totpService;
    private final AdminLoginService loginService;
    private final AdminRefreshTokenService refreshService;
    private final AdminLogoutService logoutService;
    private final AdminActionAuditor auditor;
    private final BootstrapTokenService bootstrapTokenService;

    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest body) {
        Instant startedAt = Instant.now();
        String auditId = auditor.newAuditId();
        String operatorId = body.operatorId();
        String idempotencyKey = "login:" + auditId;

        try {
            AdminLoginService.LoginResult result = loginService.login(
                    operatorId, body.password(), body.totpCode(), body.recoveryCode());
            safeRecordLogin(auditId, operatorId, Outcome.SUCCESS, null,
                    result.twofaUsed(), idempotencyKey, startedAt);
            return ResponseEntity.ok(new AdminLoginResponse(
                    result.accessToken(),
                    result.expiresIn(),
                    result.refreshToken(),
                    result.refreshExpiresIn()));
        } catch (InvalidCredentialsException ex) {
            safeRecordLogin(auditId, operatorId, Outcome.FAILURE, "INVALID_CREDENTIALS",
                    false, idempotencyKey, startedAt);
            throw ex;
        } catch (EnrollmentRequiredException ex) {
            safeRecordLogin(auditId, operatorId, Outcome.FAILURE, "ENROLLMENT_REQUIRED",
                    false, idempotencyKey, startedAt);
            throw ex;
        } catch (InvalidTwoFaCodeException ex) {
            safeRecordLogin(auditId, operatorId, Outcome.FAILURE, "INVALID_2FA_CODE",
                    false, idempotencyKey, startedAt);
            throw ex;
        } catch (InvalidRecoveryCodeException ex) {
            safeRecordLogin(auditId, operatorId, Outcome.FAILURE, "INVALID_RECOVERY_CODE",
                    false, idempotencyKey, startedAt);
            throw ex;
        } catch (InvalidLoginRequestException ex) {
            safeRecordLogin(auditId, operatorId, Outcome.FAILURE, "BAD_REQUEST",
                    false, idempotencyKey, startedAt);
            throw ex;
        }
    }

    /**
     * TASK-BE-040 — refresh-token rotation. Unauthenticated like /login but
     * presents an existing refresh JWT in the body; rotates it and returns a
     * new access+refresh pair. Reuse of an already-rotated jti triggers
     * bulk-revocation of the operator's chain (REUSE_DETECTED).
     */
    @PostMapping("/refresh")
    public ResponseEntity<AdminRefreshResponse> refresh(@Valid @RequestBody AdminRefreshRequest body) {
        Instant startedAt = Instant.now();
        String auditId = auditor.newAuditId();
        try {
            AdminRefreshTokenService.RefreshResult result = refreshService.refresh(body.refreshToken());
            // The operator UUID on `result` was read from the verified registry
            // row inside the service — never from the raw JWT payload.
            safeRecordSession(auditId, ActionCode.OPERATOR_REFRESH, result.operatorId(),
                    Outcome.SUCCESS, null, AdminActionAuditor.REASON_SELF_REFRESH,
                    "refresh:" + auditId, startedAt);
            return ResponseEntity.ok(new AdminRefreshResponse(
                    result.accessToken(), result.expiresIn(),
                    result.refreshToken(), result.refreshExpiresIn()));
        } catch (RefreshTokenReuseDetectedException ex) {
            // `ex.operatorId()` is taken from the verified registry row; the
            // presented JWT signature was valid for that row.
            safeRecordSession(auditId, ActionCode.OPERATOR_REFRESH, ex.operatorId(),
                    Outcome.FAILURE, "REUSE_DETECTED", AdminActionAuditor.REASON_SELF_REFRESH,
                    "refresh:" + auditId + ":reuse", startedAt);
            throw ex;
        } catch (InvalidRefreshTokenException ex) {
            // Signature / decode / registry lookup failed: the operator UUID
            // cannot be established from a trusted source. Per architecture.md
            // Overrides (audit-heavy A2 relaxation for /refresh), the audit row
            // is emitted without an operator id — the security log already
            // captures the raw failure.
            safeRecordSession(auditId, ActionCode.OPERATOR_REFRESH, null,
                    Outcome.FAILURE, "INVALID_REFRESH_TOKEN", AdminActionAuditor.REASON_SELF_REFRESH,
                    "refresh:" + auditId + ":invalid", startedAt);
            throw ex;
        }
    }

    /**
     * TASK-BE-040 — operator self-logout. Authenticated path: requires a valid
     * operator access JWT. Blacklists the access jti and (optionally) revokes
     * a supplied refresh token. Returns 204.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       @RequestBody(required = false) AdminLogoutRequest body) {
        OperatorContext op = currentOperator();
        if (op == null) {
            throw new OperatorUnauthorizedException("Operator JWT required for logout");
        }
        Instant startedAt = Instant.now();
        String auditId = auditor.newAuditId();
        Instant accessExp = (Instant) request.getAttribute(OperatorAuthenticationFilter.ACCESS_EXP_ATTRIBUTE);
        String refreshToken = body != null ? body.refreshToken() : null;
        try {
            logoutService.logout(op.operatorId(), op.jti(), accessExp, refreshToken);
            safeRecordSession(auditId, ActionCode.OPERATOR_LOGOUT, op.operatorId(),
                    Outcome.SUCCESS, null, AdminActionAuditor.REASON_SELF_LOGOUT,
                    "logout:" + auditId, startedAt);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (RuntimeException ex) {
            safeRecordSession(auditId, ActionCode.OPERATOR_LOGOUT, op.operatorId(),
                    Outcome.FAILURE, ex.getClass().getSimpleName(), AdminActionAuditor.REASON_SELF_LOGOUT,
                    "logout:" + auditId + ":failed", startedAt);
            throw ex;
        }
    }

    private static OperatorContext currentOperator() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof OperatorContext ctx) {
                return ctx;
            }
        } catch (RuntimeException ignored) {
            // no security context
        }
        return null;
    }

    /**
     * Single-shot audit write for /refresh and /logout. Mirrors
     * {@link #safeRecordLogin} fail-closed semantics: SUCCESS path propagates
     * audit failures (A10), FAILURE path swallows secondary audit errors so
     * the original 401/500 user-visible status is preserved (architecture.md
     * Overrides A10).
     */
    private void safeRecordSession(String auditId,
                                   ActionCode actionCode,
                                   String operatorId,
                                   Outcome outcome,
                                   String detail,
                                   String reason,
                                   String idempotencyKey,
                                   Instant startedAt) {
        try {
            auditor.record(new AdminActionAuditor.AuditRecord(
                    auditId,
                    actionCode,
                    new OperatorContext(operatorId, null),
                    "OPERATOR",
                    operatorId,
                    reason,
                    null,
                    idempotencyKey,
                    outcome,
                    detail,
                    startedAt,
                    Instant.now()));
        } catch (RuntimeException ex) {
            if (outcome == Outcome.SUCCESS) {
                throw ex;
            }
            // FAILURE path: swallow secondary audit error
        }
    }

    /**
     * TASK-BE-113 — operator self-service recovery-code regeneration. Auth: a
     * valid operator access JWT ({@code token_type=admin}). The current
     * operator UUID is taken from the {@link OperatorContext} populated by
     * {@link OperatorAuthenticationFilter}; no path/body operator id is
     * accepted (an operator may only regenerate their own codes).
     *
     * <p>Atomically replaces {@code recovery_codes_hashed} on
     * {@code admin_operator_totp}. Plain-text codes are returned exactly once
     * in the HTTP response and are NOT logged (R4 compliance).
     */
    @PostMapping("/2fa/recovery-codes/regenerate")
    public ResponseEntity<RegenerateRecoveryCodesResponse> regenerateRecoveryCodes() {
        OperatorContext op = OperatorContextHolder.require();
        String operatorId = op.operatorId();
        Instant startedAt = Instant.now();
        String auditId = auditor.newAuditId();

        try {
            java.util.List<String> codes = totpService.regenerateRecoveryCodes(operatorId);
            auditor.record(new AdminActionAuditor.AuditRecord(
                    auditId,
                    ActionCode.OPERATOR_2FA_RECOVERY_REGENERATE,
                    op,
                    "OPERATOR",
                    operatorId,
                    AdminActionAuditor.REASON_SELF_RECOVERY_REGENERATE,
                    null,
                    "regenerate:" + auditId,
                    Outcome.SUCCESS,
                    null,
                    startedAt,
                    Instant.now()));
            // Plain-text codes are intentionally NOT logged (R4 compliance).
            return ResponseEntity.ok(new RegenerateRecoveryCodesResponse(codes));
        } catch (TotpNotEnrolledException ex) {
            safeRecordRecoveryRegenerateFailure(auditId, op, "TOTP_NOT_ENROLLED", startedAt);
            throw ex;
        } catch (RuntimeException ex) {
            safeRecordRecoveryRegenerateFailure(auditId, op, ex.getClass().getSimpleName(), startedAt);
            throw ex;
        }
    }

    private void safeRecordRecoveryRegenerateFailure(String auditId,
                                                     OperatorContext op,
                                                     String detail,
                                                     Instant startedAt) {
        try {
            auditor.record(new AdminActionAuditor.AuditRecord(
                    auditId,
                    ActionCode.OPERATOR_2FA_RECOVERY_REGENERATE,
                    op,
                    "OPERATOR",
                    op.operatorId(),
                    AdminActionAuditor.REASON_SELF_RECOVERY_REGENERATE,
                    null,
                    "regenerate:" + auditId + ":failed",
                    Outcome.FAILURE,
                    detail,
                    startedAt,
                    Instant.now()));
        } catch (RuntimeException ignored) {
            // Best-effort on failure path — do not mask the original exception.
        }
    }

    @PostMapping("/2fa/enroll")
    public ResponseEntity<TotpEnrollResponse> enroll(HttpServletRequest request) {
        BootstrapContext bootstrap = requireBootstrap(request);
        String operatorId = bootstrap.operatorId();
        Instant startedAt = Instant.now();
        String auditId = auditor.newAuditId();

        try {
            TotpEnrollmentService.EnrollmentResult result = totpService.enroll(operatorId);
            auditor.record(new AdminActionAuditor.AuditRecord(
                    auditId,
                    ActionCode.OPERATOR_2FA_ENROLL,
                    new OperatorContext(operatorId, bootstrap.jti()),
                    "OPERATOR",
                    operatorId,
                    AdminActionAuditor.REASON_SELF_ENROLLMENT,
                    null,
                    "bootstrap:" + bootstrap.jti(),
                    Outcome.SUCCESS,
                    null,
                    startedAt,
                    Instant.now()));
            BootstrapTokenService.Issued verifyToken = bootstrapTokenService.issue(
                    operatorId, java.util.Set.of(BootstrapTokenService.SCOPE_VERIFY));
            long ttl = java.time.Duration.between(Instant.now(), verifyToken.expiresAt()).getSeconds();
            if (ttl < 0) ttl = 0;
            return ResponseEntity.ok(new TotpEnrollResponse(
                    result.otpauthUri(), result.recoveryCodes(), result.enrolledAt(),
                    verifyToken.token(), ttl));
        } catch (RuntimeException ex) {
            safeRecordFailure(auditId, ActionCode.OPERATOR_2FA_ENROLL, operatorId, bootstrap.jti(),
                    ex.getClass().getSimpleName(), startedAt);
            throw ex;
        }
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<TotpVerifyResponse> verify(HttpServletRequest request,
                                                     @Valid @RequestBody TotpVerifyRequest body) {
        BootstrapContext bootstrap = requireBootstrap(request);
        String operatorId = bootstrap.operatorId();
        Instant startedAt = Instant.now();
        String auditId = auditor.newAuditId();

        try {
            totpService.verify(operatorId, body.totpCode());
            auditor.record(new AdminActionAuditor.AuditRecord(
                    auditId,
                    ActionCode.OPERATOR_2FA_VERIFY,
                    new OperatorContext(operatorId, bootstrap.jti()),
                    "OPERATOR",
                    operatorId,
                    AdminActionAuditor.REASON_SELF_ENROLLMENT,
                    null,
                    "bootstrap:" + bootstrap.jti(),
                    Outcome.SUCCESS,
                    null,
                    startedAt,
                    Instant.now()));
            return ResponseEntity.ok(new TotpVerifyResponse(true));
        } catch (InvalidTwoFaCodeException ex) {
            safeRecordFailure(auditId, ActionCode.OPERATOR_2FA_VERIFY, operatorId, bootstrap.jti(),
                    "INVALID_2FA_CODE", startedAt);
            throw ex;
        }
    }

    private void safeRecordFailure(String auditId,
                                   ActionCode actionCode,
                                   String operatorId,
                                   String jti,
                                   String detail,
                                   Instant startedAt) {
        try {
            auditor.record(new AdminActionAuditor.AuditRecord(
                    auditId,
                    actionCode,
                    new OperatorContext(operatorId, jti),
                    "OPERATOR",
                    operatorId,
                    AdminActionAuditor.REASON_SELF_ENROLLMENT,
                    null,
                    "bootstrap:" + jti + ":failed",
                    Outcome.FAILURE,
                    detail,
                    startedAt,
                    Instant.now()));
        } catch (RuntimeException ignored) {
            // Best-effort on failure path — do not mask the original exception.
        }
    }

    /**
     * Records the login audit row. Success path propagates
     * {@code AuditFailureException} (fail-closed per audit-heavy A10).
     * FAILURE path swallows secondary audit errors so the original 401/400
     * user-visible status is not masked — this is a documented override
     * against A10. See architecture.md#Overrides A10.
     */
    private void safeRecordLogin(String auditId,
                                 String operatorId,
                                 Outcome outcome,
                                 String detail,
                                 boolean twofaUsed,
                                 String idempotencyKey,
                                 Instant startedAt) {
        try {
            auditor.recordLogin(new AdminActionAuditor.LoginAuditRecord(
                    auditId,
                    new OperatorContext(operatorId, null),
                    "OPERATOR",
                    operatorId,
                    AdminActionAuditor.REASON_SELF_LOGIN,
                    outcome == Outcome.SUCCESS ? idempotencyKey : idempotencyKey + ":failed",
                    outcome,
                    detail,
                    twofaUsed,
                    startedAt,
                    Instant.now()));
        } catch (RuntimeException ex) {
            // Audit fail-closed is already enforced inside recordLogin for the
            // success path (AuditFailureException propagates). On FAILURE paths
            // we intentionally swallow secondary audit errors so the original
            // login failure (401/400) is not masked.
            if (outcome == Outcome.SUCCESS) {
                throw ex;
            }
        }
    }

    private static BootstrapContext requireBootstrap(HttpServletRequest request) {
        Object attr = request.getAttribute(BootstrapContext.ATTRIBUTE);
        if (attr instanceof BootstrapContext ctx) {
            return ctx;
        }
        throw new InvalidBootstrapTokenException("Bootstrap context missing on request");
    }
}

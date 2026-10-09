package com.example.admin.application;

import com.example.admin.application.exception.AccountSecondFactorNotEnrolledException;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.exception.TargetAccountNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AccountSecondFactorResetPort;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.Permission;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * TASK-MONO-771 S6 (ADR-MONO-080 D4, ticket Edge Case 2, owner decision OD-6) — reset another account's
 * account-plane second factor: the way back for a person who lost both the authenticator app and the recovery
 * codes (admin-api.md § POST /api/admin/accounts/{accountId}/2fa/reset).
 *
 * <p><b>Who.</b> {@code account.2fa_reset} is the endpoint gate ({@code @RequiresPermission}; seeded to
 * {@code SUPER_ADMIN} · {@code SECURITY_ANALYST} only, V0049). Holding the key is not enough: the operator must be
 * PLATFORM scope ({@code '*'}) — checked here, before anything is written or called. A key-holder provisioned under
 * a customer tenant gets 403 {@code TENANT_SCOPE_DENIED} and a best-effort DENIED row (OD-6: an account is a
 * personal pool account; a company is not given the power to lower its security).
 *
 * <p><b>Flow</b> — the {@link AccountAdminUseCase} lock shape: reason → platform scope → idempotency-key reuse
 * (409, no row) → IN_PROGRESS audit row (A10 fail-closed) → auth-service internal command → SUCCESS / FAILURE
 * completion row. Every started action ends in a completion row, including a downstream 404 and an open circuit.
 *
 * <p><b>What it does not do.</b> It does not end sessions (contract: a session that already passed the second step
 * stays; a suspected takeover is a separate {@code POST /api/admin/sessions/{accountId}/revoke}). After the reset the
 * account has no enrolment, so its next entry that requires a second factor is routed to enrolment (OD-4 — verified
 * e-mail precondition and notice mail are auth-service's, S2b).
 */
@Service
@RequiredArgsConstructor
public class AccountSecondFactorResetUseCase {

    private static final ActionCode ACTION = ActionCode.ACCOUNT_2FA_RESET;
    private static final String TARGET_TYPE = "ACCOUNT";

    private final AccountSecondFactorResetPort resetPort;
    private final OperatorLookupPort operatorLookupPort;
    private final AdminActionAuditor auditor;

    public ResetAccountSecondFactorResult reset(ResetAccountSecondFactorCommand cmd) {
        AuditReasons.require(cmd.reason());
        requirePlatformScope(cmd.operator());

        // Same reason as TASK-MONO-735 on lock: a re-sent key must not reach recordStart (its INSERT would die on
        // idx_admin_actions_idemp → 500). 409, no row, no downstream call.
        if (auditor.isIdempotencyKeyUsed(cmd.operator().operatorId(), ACTION, cmd.idempotencyKey())) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key already used for " + ACTION + " by this operator; retry with a new key");
        }

        String auditId = auditor.newAuditId();
        Instant startedAt = Instant.now();
        auditor.recordStart(new AdminActionAuditor.StartRecord(
                auditId, ACTION, cmd.operator(),
                TARGET_TYPE, cmd.accountId(),
                cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                startedAt));

        AccountSecondFactorResetPort.ResetResult downstream;
        try {
            downstream = resetPort.reset(
                    cmd.accountId(), cmd.operator().operatorId(), cmd.reason(), cmd.idempotencyKey());
        } catch (CallNotPermittedException ex) {
            recordFailure(cmd, auditId, startedAt, "CIRCUIT_OPEN: " + ex.getMessage());
            throw ex;
        } catch (DownstreamFailureException ex) {
            recordFailure(cmd, auditId, startedAt, failureDetail(ex));
            throw toOperatorFacing(ex);
        }

        Instant completedAt = Instant.now();
        auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                auditId, ACTION, cmd.operator(),
                TARGET_TYPE, cmd.accountId(),
                cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                Outcome.SUCCESS, "wasConfirmed=" + downstream.wasConfirmed(),
                startedAt, completedAt));

        return new ResetAccountSecondFactorResult(
                cmd.accountId(),
                cmd.operator().operatorId(),
                downstream.resetAt() != null ? downstream.resetAt() : completedAt,
                auditId);
    }

    /**
     * OD-6 second gate: the operator's own scope must be the platform ({@code '*'}). The key alone is not trusted —
     * a {@code SECURITY_ANALYST} provisioned under a customer tenant would otherwise reset pool accounts.
     */
    private void requirePlatformScope(OperatorContext operator) {
        OperatorLookupPort.OperatorLookupRef ref = operatorLookupPort.findByOperatorId(operator.operatorId())
                .orElseThrow(() -> new TenantScopeDeniedException(
                        "Operator not found: " + operator.operatorId()));
        if (!AdminOperator.PLATFORM_TENANT_ID.equals(ref.tenantId())) {
            auditor.recordCrossTenantDenied(operator, ref.tenantId(), ACTION,
                    Permission.ACCOUNT_2FA_RESET, AdminOperator.PLATFORM_TENANT_ID);
            throw new TenantScopeDeniedException(
                    "Only platform-scope operators may reset an account's second factor");
        }
    }

    /**
     * auth-service's definitive 404s become the public contract's two 404s; everything else stays a downstream
     * failure (503 {@code DOWNSTREAM_ERROR}).
     */
    private static RuntimeException toOperatorFacing(DownstreamFailureException ex) {
        if (ex instanceof NonRetryableDownstreamException nr && nr.getHttpStatus() == 404) {
            if ("TOTP_NOT_ENROLLED".equals(nr.getErrorCode())) {
                return new AccountSecondFactorNotEnrolledException(
                        "The account has no second-factor enrolment to reset");
            }
            if ("ACCOUNT_NOT_FOUND".equals(nr.getErrorCode())) {
                return new TargetAccountNotFoundException("Target account not found", nr);
            }
        }
        return ex;
    }

    private static String failureDetail(DownstreamFailureException ex) {
        if (ex instanceof NonRetryableDownstreamException nr && nr.getErrorCode() != null) {
            return nr.getErrorCode();
        }
        return ex.getMessage();
    }

    private void recordFailure(ResetAccountSecondFactorCommand cmd, String auditId, Instant startedAt,
                               String failureMessage) {
        auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                auditId, ACTION, cmd.operator(),
                TARGET_TYPE, cmd.accountId(),
                cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                Outcome.FAILURE, failureMessage,
                startedAt, Instant.now()));
    }
}

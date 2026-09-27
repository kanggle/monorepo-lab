package com.example.admin.application;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.exception.StateTransitionInvalidException;
import com.example.admin.application.exception.TargetAccountNotFoundException;
import com.example.admin.infrastructure.client.AccountServiceClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.function.Function;

/**
 * Orchestrates lock/unlock operator commands. Authorization is enforced
 * upstream by {@code RequiresPermissionAspect}; this layer only validates
 * request payloads and drives the audit + downstream flow.
 *
 * <p>Flow per command: validate reason → INSERT IN_PROGRESS audit row (A10
 * fail-closed) → call account-service internal HTTP (with Idempotency-Key) →
 * finalize audit row (SUCCESS or FAILURE) and emit the outbox event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountAdminUseCase {

    private final AccountServiceClient accountServiceClient;
    private final AdminActionAuditor auditor;

    public LockAccountResult lock(LockAccountCommand cmd) {
        AuditReasons.require(cmd.reason());
        AccountActionOutcome outcome = executeAccountAction(
                ActionCode.ACCOUNT_LOCK,
                cmd.operator(), cmd.accountId(),
                cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                ik -> accountServiceClient.lock(
                        cmd.accountId(),
                        cmd.operator().operatorId(),
                        cmd.reason(),
                        cmd.ticketId(),
                        ik,
                        cmd.tenantId()));
        return new LockAccountResult(
                outcome.downstream.accountId(),
                outcome.downstream.previousStatus(),
                outcome.downstream.currentStatus(),
                cmd.operator().operatorId(),
                outcome.downstream.lockedAt() != null ? outcome.downstream.lockedAt() : outcome.completedAt,
                outcome.auditId);
    }

    public UnlockAccountResult unlock(UnlockAccountCommand cmd) {
        AuditReasons.require(cmd.reason());
        AccountActionOutcome outcome = executeAccountAction(
                ActionCode.ACCOUNT_UNLOCK,
                cmd.operator(), cmd.accountId(),
                cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                ik -> accountServiceClient.unlock(
                        cmd.accountId(),
                        cmd.operator().operatorId(),
                        cmd.reason(),
                        cmd.ticketId(),
                        ik,
                        cmd.tenantId()));
        return new UnlockAccountResult(
                outcome.downstream.accountId(),
                outcome.downstream.previousStatus(),
                outcome.downstream.currentStatus(),
                cmd.operator().operatorId(),
                outcome.downstream.unlockedAt() != null ? outcome.downstream.unlockedAt() : outcome.completedAt,
                outcome.auditId);
    }

    /**
     * Common flow shared by {@link #lock} and {@link #unlock}:
     * <ol>
     *   <li>INSERT IN_PROGRESS audit row (A10 fail-closed).</li>
     *   <li>Call account-service downstream with the supplied {@code downstreamCall}.</li>
     *   <li>On success: finalize audit row to SUCCESS.</li>
     *   <li>On circuit-breaker open or downstream failure: finalize to FAILURE and rethrow.</li>
     * </ol>
     *
     * @param actionCode     the audit action code (ACCOUNT_LOCK or ACCOUNT_UNLOCK)
     * @param operator       the acting operator context
     * @param accountId      the target account
     * @param reason         mandatory operator reason
     * @param ticketId       optional ticket reference
     * @param idempotencyKey caller-supplied idempotency key
     * @param downstreamCall function from the effective idempotency key to the downstream response
     * @return outcome carrying the auditId, completedAt timestamp, and downstream response
     */
    private AccountActionOutcome executeAccountAction(
            ActionCode actionCode,
            OperatorContext operator,
            String accountId,
            String reason,
            String ticketId,
            String idempotencyKey,
            Function<String, AccountServiceClient.LockResponse> downstreamCall) {

        // TASK-MONO-735: a second request with the same (operator, action, key) — the console's
        // confirm dialog keeps one key per confirmed action, so "press confirm again" re-sends
        // it — must not reach recordStart: its INSERT dies on idx_admin_actions_idemp and the
        // operator sees 500 AUDIT_FAILURE for what was a definitive downstream answer (measured
        // live 2026-09-26). Answer 409 IDEMPOTENCY_KEY_CONFLICT instead: no new audit row, no
        // downstream call. The @Retry on AccountServiceClient is NOT the re-invoker — it sits
        // below this method, never re-enters recordStart, and already ignores 4xx.
        // Residual: two truly concurrent first requests can still race past this read; the loser
        // gets the unique-key 500 as before (recorded in the ticket, not fixed here).
        if (auditor.isIdempotencyKeyUsed(operator.operatorId(), actionCode, idempotencyKey)) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key already used for " + actionCode + " by this operator; "
                            + "retry with a new key");
        }

        String auditId = auditor.newAuditId();
        Instant startedAt = Instant.now();

        auditor.recordStart(new AdminActionAuditor.StartRecord(
                auditId, actionCode, operator,
                "ACCOUNT", accountId,
                reason, ticketId, idempotencyKey,
                startedAt));

        try {
            AccountServiceClient.LockResponse downstream = downstreamCall.apply(idempotencyKey);
            Instant completedAt = Instant.now();
            auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                    auditId, actionCode, operator,
                    "ACCOUNT", accountId,
                    reason, ticketId, idempotencyKey,
                    Outcome.SUCCESS, null, startedAt, completedAt));
            return new AccountActionOutcome(auditId, completedAt, downstream);
        } catch (CallNotPermittedException ex) {
            // Circuit breaker OPEN: downstream call was rejected. Record FAILURE
            // audit row before re-throwing so AdminExceptionHandler maps to 503
            // CIRCUIT_OPEN. A10 fail-closed requires a completion row for every
            // started action, including CB-rejected ones.
            recordAuditFailure(auditId, actionCode, operator, accountId,
                    reason, ticketId, idempotencyKey, startedAt,
                    "CIRCUIT_OPEN: " + ex.getMessage());
            throw ex;
        } catch (DownstreamFailureException ex) {
            recordAuditFailure(auditId, actionCode, operator, accountId,
                    reason, ticketId, idempotencyKey, startedAt, ex.getMessage());
            throw toOperatorFacing(ex);
        }
    }

    /**
     * TASK-MONO-735 — surface account-service's definitive 4xx answers as admin-api.md specifies
     * for lock/unlock ({@code 404 ACCOUNT_NOT_FOUND}, {@code 400 STATE_TRANSITION_INVALID})
     * instead of the blanket {@code 503 DOWNSTREAM_ERROR}, which told the operator to retry a
     * call that cannot change. Any other failure is re-thrown unchanged (still 503).
     */
    private static RuntimeException toOperatorFacing(DownstreamFailureException ex) {
        if (ex instanceof NonRetryableDownstreamException nr) {
            if (nr.getHttpStatus() == 404) {
                return new TargetAccountNotFoundException("Target account not found", nr);
            }
            if (nr.getHttpStatus() == 409) {
                return new StateTransitionInvalidException("Account state transition invalid");
            }
        }
        return ex;
    }

    /** Carries the success-path results of {@link #executeAccountAction}. */
    private record AccountActionOutcome(
            String auditId,
            Instant completedAt,
            AccountServiceClient.LockResponse downstream
    ) {}

    private void recordAuditFailure(String auditId, ActionCode actionCode,
                                    OperatorContext operator, String targetId,
                                    String reason, String ticketId, String idempotencyKey,
                                    Instant startedAt, String failureMessage) {
        auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                auditId, actionCode, operator,
                "ACCOUNT", targetId,
                reason, ticketId, idempotencyKey,
                Outcome.FAILURE, failureMessage,
                startedAt, Instant.now()));
    }
}

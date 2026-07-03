package com.example.admin.application;

import com.example.admin.application.exception.DownstreamFailureException;
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
            throw ex;
        }
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

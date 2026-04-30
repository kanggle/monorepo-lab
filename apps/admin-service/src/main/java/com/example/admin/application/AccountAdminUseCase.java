package com.example.admin.application;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.infrastructure.client.AccountServiceClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

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
        requireReason(cmd.reason());

        String auditId = auditor.newAuditId();
        Instant startedAt = Instant.now();

        auditor.recordStart(new AdminActionAuditor.StartRecord(
                auditId, ActionCode.ACCOUNT_LOCK, cmd.operator(),
                "ACCOUNT", cmd.accountId(),
                cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                startedAt));

        AccountServiceClient.LockResponse downstream;
        try {
            downstream = accountServiceClient.lock(
                    cmd.accountId(),
                    cmd.operator().operatorId(),
                    cmd.reason(),
                    cmd.ticketId(),
                    cmd.idempotencyKey());
        } catch (CallNotPermittedException ex) {
            // Circuit breaker OPEN: downstream call was rejected. Record FAILURE
            // audit row before re-throwing so AdminExceptionHandler maps to 503
            // CIRCUIT_OPEN. A10 fail-closed requires a completion row for every
            // started action, including CB-rejected ones.
            recordAuditFailure(auditId, ActionCode.ACCOUNT_LOCK, cmd.operator(),
                    cmd.accountId(), cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                    startedAt, "CIRCUIT_OPEN: " + ex.getMessage());
            throw ex;
        } catch (DownstreamFailureException ex) {
            recordAuditFailure(auditId, ActionCode.ACCOUNT_LOCK, cmd.operator(),
                    cmd.accountId(), cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                    startedAt, ex.getMessage());
            throw ex;
        }

        Instant completedAt = Instant.now();
        auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                auditId, ActionCode.ACCOUNT_LOCK, cmd.operator(),
                "ACCOUNT", cmd.accountId(),
                cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                Outcome.SUCCESS, null, startedAt, completedAt));

        return new LockAccountResult(
                downstream.accountId(),
                downstream.previousStatus(),
                downstream.currentStatus(),
                cmd.operator().operatorId(),
                downstream.lockedAt() != null ? downstream.lockedAt() : completedAt,
                auditId);
    }

    public UnlockAccountResult unlock(UnlockAccountCommand cmd) {
        requireReason(cmd.reason());

        String auditId = auditor.newAuditId();
        Instant startedAt = Instant.now();

        auditor.recordStart(new AdminActionAuditor.StartRecord(
                auditId, ActionCode.ACCOUNT_UNLOCK, cmd.operator(),
                "ACCOUNT", cmd.accountId(),
                cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                startedAt));

        AccountServiceClient.LockResponse downstream;
        try {
            downstream = accountServiceClient.unlock(
                    cmd.accountId(),
                    cmd.operator().operatorId(),
                    cmd.reason(),
                    cmd.ticketId(),
                    cmd.idempotencyKey());
        } catch (CallNotPermittedException ex) {
            recordAuditFailure(auditId, ActionCode.ACCOUNT_UNLOCK, cmd.operator(),
                    cmd.accountId(), cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                    startedAt, "CIRCUIT_OPEN: " + ex.getMessage());
            throw ex;
        } catch (DownstreamFailureException ex) {
            recordAuditFailure(auditId, ActionCode.ACCOUNT_UNLOCK, cmd.operator(),
                    cmd.accountId(), cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                    startedAt, ex.getMessage());
            throw ex;
        }

        Instant completedAt = Instant.now();
        auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                auditId, ActionCode.ACCOUNT_UNLOCK, cmd.operator(),
                "ACCOUNT", cmd.accountId(),
                cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                Outcome.SUCCESS, null, startedAt, completedAt));

        return new UnlockAccountResult(
                downstream.accountId(),
                downstream.previousStatus(),
                downstream.currentStatus(),
                cmd.operator().operatorId(),
                downstream.unlockedAt() != null ? downstream.unlockedAt() : completedAt,
                auditId);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ReasonRequiredException();
        }
    }

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

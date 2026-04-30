package com.example.admin.application;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.infrastructure.client.AuthServiceClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SessionAdminUseCase {

    private final AuthServiceClient authServiceClient;
    private final AdminActionAuditor auditor;

    public RevokeSessionResult revoke(RevokeSessionCommand cmd) {
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new ReasonRequiredException();
        }

        String auditId = auditor.newAuditId();
        Instant startedAt = Instant.now();

        auditor.recordStart(new AdminActionAuditor.StartRecord(
                auditId, ActionCode.SESSION_REVOKE, cmd.operator(),
                "SESSION", cmd.accountId(),
                cmd.reason(), null, cmd.idempotencyKey(),
                startedAt));

        AuthServiceClient.ForceLogoutResponse downstream;
        try {
            downstream = authServiceClient.forceLogout(
                    cmd.accountId(),
                    cmd.operator().operatorId(),
                    cmd.reason(),
                    cmd.idempotencyKey());
        } catch (CallNotPermittedException ex) {
            // Circuit breaker OPEN on auth-service force-logout: record FAILURE
            // completion before re-throw so AdminExceptionHandler maps to 503
            // CIRCUIT_OPEN (A10 fail-closed).
            recordAuditFailure(auditId, ActionCode.SESSION_REVOKE, cmd.operator(),
                    cmd.accountId(), cmd.reason(), cmd.idempotencyKey(),
                    startedAt, "CIRCUIT_OPEN: " + ex.getMessage());
            throw ex;
        } catch (DownstreamFailureException ex) {
            recordAuditFailure(auditId, ActionCode.SESSION_REVOKE, cmd.operator(),
                    cmd.accountId(), cmd.reason(), cmd.idempotencyKey(),
                    startedAt, ex.getMessage());
            throw ex;
        }

        Instant completedAt = Instant.now();
        auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                auditId, ActionCode.SESSION_REVOKE, cmd.operator(),
                "SESSION", cmd.accountId(),
                cmd.reason(), null, cmd.idempotencyKey(),
                Outcome.SUCCESS, null, startedAt, completedAt));

        int count = downstream.revokedTokenCount() == null ? 0 : downstream.revokedTokenCount();
        return new RevokeSessionResult(
                downstream.accountId(),
                count,
                cmd.operator().operatorId(),
                downstream.revokedAt() != null ? downstream.revokedAt() : completedAt,
                auditId);
    }

    private void recordAuditFailure(String auditId, ActionCode actionCode,
                                    OperatorContext operator, String targetId,
                                    String reason, String idempotencyKey,
                                    Instant startedAt, String failureMessage) {
        auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                auditId, actionCode, operator,
                "SESSION", targetId,
                reason, null, idempotencyKey,
                Outcome.FAILURE, failureMessage,
                startedAt, Instant.now()));
    }
}

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
 * Orchestrates GDPR/PIPA operator commands: deletion with PII masking
 * and personal data export. Follows the same audit pattern as
 * {@link AccountAdminUseCase}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdprAdminUseCase {

    private final AccountServiceClient accountServiceClient;
    private final AdminActionAuditor auditor;

    public GdprDeleteResult gdprDelete(GdprDeleteCommand cmd) {
        requireReason(cmd.reason());

        String auditId = auditor.newAuditId();
        Instant startedAt = Instant.now();

        auditor.recordStart(new AdminActionAuditor.StartRecord(
                auditId, ActionCode.GDPR_DELETE, cmd.operator(),
                "ACCOUNT", cmd.accountId(),
                cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                startedAt));

        AccountServiceClient.GdprDeleteResponse downstream;
        try {
            downstream = accountServiceClient.gdprDelete(
                    cmd.accountId(),
                    cmd.operator().operatorId(),
                    cmd.idempotencyKey());
        } catch (CallNotPermittedException ex) {
            recordAuditFailure(auditId, ActionCode.GDPR_DELETE, cmd.operator(),
                    cmd.accountId(), cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                    startedAt, "CIRCUIT_OPEN: " + ex.getMessage());
            throw ex;
        } catch (DownstreamFailureException ex) {
            recordAuditFailure(auditId, ActionCode.GDPR_DELETE, cmd.operator(),
                    cmd.accountId(), cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                    startedAt, ex.getMessage());
            throw ex;
        }

        Instant completedAt = Instant.now();
        auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                auditId, ActionCode.GDPR_DELETE, cmd.operator(),
                "ACCOUNT", cmd.accountId(),
                cmd.reason(), cmd.ticketId(), cmd.idempotencyKey(),
                Outcome.SUCCESS, null, startedAt, completedAt));

        return new GdprDeleteResult(
                downstream.accountId(),
                downstream.status(),
                downstream.maskedAt() != null ? downstream.maskedAt() : completedAt,
                auditId);
    }

    public DataExportResult dataExport(String accountId, OperatorContext operator, String reason) {
        requireReason(reason);

        String auditId = auditor.newAuditId();
        Instant startedAt = Instant.now();

        // Meta-audit: data export access is logged as a single-shot record
        AccountServiceClient.DataExportResponse downstream;
        try {
            downstream = accountServiceClient.export(accountId, operator.operatorId());
        } catch (CallNotPermittedException ex) {
            recordSingleShotAuditFailure(auditId, ActionCode.DATA_EXPORT, operator,
                    accountId, reason, startedAt, "CIRCUIT_OPEN: " + ex.getMessage());
            throw ex;
        } catch (DownstreamFailureException ex) {
            recordSingleShotAuditFailure(auditId, ActionCode.DATA_EXPORT, operator,
                    accountId, reason, startedAt, ex.getMessage());
            throw ex;
        }

        Instant completedAt = Instant.now();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId, ActionCode.DATA_EXPORT, operator,
                "ACCOUNT", accountId,
                reason, null, null,
                Outcome.SUCCESS, null,
                startedAt, completedAt));

        DataExportResult.ProfileData profileData = null;
        if (downstream.profile() != null) {
            profileData = new DataExportResult.ProfileData(
                    downstream.profile().displayName(),
                    downstream.profile().phoneNumber(),
                    downstream.profile().birthDate(),
                    downstream.profile().locale(),
                    downstream.profile().timezone());
        }

        return new DataExportResult(
                downstream.accountId(),
                downstream.email(),
                downstream.status(),
                downstream.createdAt(),
                profileData,
                downstream.exportedAt() != null ? downstream.exportedAt() : completedAt);
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

    private void recordSingleShotAuditFailure(String auditId, ActionCode actionCode,
                                              OperatorContext operator, String targetId,
                                              String reason, Instant startedAt,
                                              String failureMessage) {
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId, actionCode, operator,
                "ACCOUNT", targetId,
                reason, null, null,
                Outcome.FAILURE, failureMessage,
                startedAt, Instant.now()));
    }
}

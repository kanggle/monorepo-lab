package com.example.admin.application.tenant;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.Outcome;
import com.example.admin.application.event.TenantEventPublisher;
import com.example.admin.application.port.TenantProvisioningPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * TASK-BE-250: Updates an existing tenant's displayName and/or status.
 *
 * <p>Single PATCH endpoint handles both displayName and status changes. Status transition
 * matrix (ACTIVE ↔ SUSPENDED) is validated. Same-status PATCH is a no-op (200, no
 * audit row, no event). Both fields can change simultaneously — status event is emitted
 * first (higher security/audit value), then displayName event.
 *
 * <p>Note: this use case reads current state from account-service (via port) to detect
 * no-ops and compute change diffs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateTenantUseCase {

    private final TenantProvisioningPort provisioningPort;
    private final AdminActionAuditor auditor;
    private final TenantEventPublisher tenantEventPublisher;

    @Transactional
    public TenantSummary execute(String tenantId, String newDisplayName, String newStatus,
                                 OperatorContext operator, String reason, String idempotencyKey) {

        // Validate: at least one field must be provided
        if ((newDisplayName == null || newDisplayName.isBlank())
                && (newStatus == null || newStatus.isBlank())) {
            throw new IllegalArgumentException(
                    "At least one of displayName or status must be provided");
        }

        // Validate displayName length if provided
        if (newDisplayName != null && !newDisplayName.isBlank()
                && newDisplayName.trim().length() > 100) {
            throw new IllegalArgumentException("displayName must be 1–100 characters");
        }

        // Validate status enum if provided
        TenantStatusEnum targetStatus = null;
        if (newStatus != null && !newStatus.isBlank()) {
            try {
                targetStatus = TenantStatusEnum.valueOf(newStatus);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "status must be ACTIVE or SUSPENDED: " + newStatus);
            }
        }

        // Read current state to detect no-ops and compute diffs
        TenantSummary current = provisioningPort.get(tenantId);

        boolean statusChanged = targetStatus != null
                && !targetStatus.name().equals(current.status());
        boolean displayNameChanged = newDisplayName != null && !newDisplayName.isBlank()
                && !newDisplayName.trim().equals(current.displayName());

        // If nothing actually changes, return 200 no-op (no audit, no event)
        if (!statusChanged && !displayNameChanged) {
            return current;
        }

        // Determine effective PATCH values to send to account-service
        String effectiveDisplayName = displayNameChanged ? newDisplayName.trim() : null;
        String effectiveStatus = statusChanged ? newStatus : null;

        Instant now = Instant.now();

        // --- Status change (higher severity — emit first) ---
        if (statusChanged) {
            ActionCode actionCode = "SUSPENDED".equals(newStatus)
                    ? ActionCode.TENANT_SUSPEND : ActionCode.TENANT_REACTIVATE;

            String auditId = auditor.newAuditId();
            auditor.recordStart(new AdminActionAuditor.StartRecord(
                    auditId, actionCode, operator,
                    "TENANT", tenantId,
                    reason != null ? reason : "<not_provided>",
                    null, idempotencyKey != null ? idempotencyKey + ":status" : "status:" + auditId,
                    now, tenantId));

            try {
                TenantSummary updated = provisioningPort.update(tenantId, effectiveDisplayName, effectiveStatus);

                auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                        auditId, actionCode, operator,
                        "TENANT", tenantId,
                        reason != null ? reason : "<not_provided>",
                        null, idempotencyKey != null ? idempotencyKey + ":status" : "status:" + auditId,
                        Outcome.SUCCESS, null, now, Instant.now()));

                if ("SUSPENDED".equals(newStatus)) {
                    tenantEventPublisher.publishTenantSuspended(
                            tenantId, operator.operatorId(), reason, now);
                } else {
                    tenantEventPublisher.publishTenantReactivated(
                            tenantId, operator.operatorId(), reason, now);
                }

                // If displayName also changed, emit displayName event too
                if (displayNameChanged) {
                    String dnAuditId = auditor.newAuditId();
                    auditor.recordStart(new AdminActionAuditor.StartRecord(
                            dnAuditId, ActionCode.TENANT_UPDATE, operator,
                            "TENANT", tenantId,
                            reason != null ? reason : "<not_provided>",
                            null, idempotencyKey != null ? idempotencyKey + ":dn" : "dn:" + dnAuditId,
                            now, tenantId));

                    auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                            dnAuditId, ActionCode.TENANT_UPDATE, operator,
                            "TENANT", tenantId,
                            reason != null ? reason : "<not_provided>",
                            null, idempotencyKey != null ? idempotencyKey + ":dn" : "dn:" + dnAuditId,
                            Outcome.SUCCESS, null, now, Instant.now()));

                    tenantEventPublisher.publishTenantUpdated(
                            tenantId, current.displayName(), newDisplayName.trim(),
                            operator.operatorId(), now);
                }

                return updated;

            } catch (RuntimeException ex) {
                auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                        auditId, actionCode, operator,
                        "TENANT", tenantId,
                        reason != null ? reason : "<not_provided>",
                        null, idempotencyKey != null ? idempotencyKey + ":status" : "status:" + auditId,
                        Outcome.FAILURE, ex.getMessage(), now, Instant.now()));
                throw ex;
            }
        }

        // --- displayName-only change ---
        String auditId = auditor.newAuditId();
        auditor.recordStart(new AdminActionAuditor.StartRecord(
                auditId, ActionCode.TENANT_UPDATE, operator,
                "TENANT", tenantId,
                reason != null ? reason : "<not_provided>",
                null, idempotencyKey != null ? idempotencyKey : "dn:" + auditId,
                now, tenantId));

        try {
            TenantSummary updated = provisioningPort.update(tenantId, effectiveDisplayName, null);

            auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                    auditId, ActionCode.TENANT_UPDATE, operator,
                    "TENANT", tenantId,
                    reason != null ? reason : "<not_provided>",
                    null, idempotencyKey != null ? idempotencyKey : "dn:" + auditId,
                    Outcome.SUCCESS, null, now, Instant.now()));

            tenantEventPublisher.publishTenantUpdated(
                    tenantId, current.displayName(), newDisplayName.trim(),
                    operator.operatorId(), now);

            return updated;

        } catch (RuntimeException ex) {
            auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                    auditId, ActionCode.TENANT_UPDATE, operator,
                    "TENANT", tenantId,
                    reason != null ? reason : "<not_provided>",
                    null, idempotencyKey != null ? idempotencyKey : "dn:" + auditId,
                    Outcome.FAILURE, ex.getMessage(), now, Instant.now()));
            throw ex;
        }
    }
}

package com.example.admin.application.tenant;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.OperatorInvitationWriter;
import com.example.admin.application.Outcome;
import com.example.admin.application.event.TenantEventPublisher;
import com.example.admin.application.exception.TenantStatusConflictException;
import com.example.admin.application.port.OperatorInvitationPort;
import com.example.admin.application.port.OperatorInvitationPort.InvitationView;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-250: Updates an existing tenant's displayName and/or status.
 *
 * <p>Single PATCH endpoint handles both displayName and status changes. Same-status PATCH is a no-op (200, no
 * audit row, no event). Both fields can change simultaneously — status event is emitted first (higher
 * security/audit value), then displayName event.
 *
 * <p>Note: this use case reads current state from account-service (via port) to detect no-ops and compute change
 * diffs.
 *
 * <p><b>TASK-MONO-773 S4 — the {@code PENDING} («관리자 대기») rules</b> (admin-api.md § Status 전이 매트릭스 ·
 * § PATCH «PENDING 테넌트에 대한 PATCH»). 🔴 Invariant (ADR-044 D3): no write here makes an {@code ACTIVE} tenant
 * whose first admin never existed.
 * <ul>
 *   <li>{@code PENDING → ACTIVE}: {@code 409 TENANT_FIRST_ADMIN_PENDING} unless the tenant's first-admin
 *       invitation is already {@code ACCEPTED} — then it is the recovery path: {@link TenantActivation}
 *       ({@code TENANT_ACTIVATE} + {@code tenant.activated}, trigger {@code OPERATOR_COMPLETED});</li>
 *   <li>{@code PENDING → SUSPENDED} = «정리» (OD-2): FIRST the pending first-admin invitation is cancelled (its
 *       own committed transaction + {@code OPERATOR_INVITATION_CANCEL}), THEN account-service suspends, then
 *       {@code TENANT_SUSPEND} + {@code tenant.suspended(previousStatus=PENDING)}. A suspend failure leaves «invitation
 *       cancelled, tenant PENDING»; the same PATCH again continues;</li>
 *   <li>{@code SUSPENDED → ACTIVE} of a cleaned-up tenant (first-admin rows exist, none {@code ACCEPTED}) → 409 —
 *       a tenant with no first-admin row (pre-773 · self-service) reactivates as before.</li>
 * </ul>
 * Not {@code @Transactional} as a whole (since S4): the cancel of «정리» must be committed before the account-service
 * call, so a failed suspend cannot roll it back. Each write below carries its own transaction (the audit
 * start/completion are {@code REQUIRES_NEW}; the outbox publisher and the invitation writer open their own).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateTenantUseCase {

    static final String PENDING = "PENDING";

    private final TenantProvisioningPort provisioningPort;
    private final AdminActionAuditor auditor;
    private final TenantEventPublisher tenantEventPublisher;
    /** TASK-MONO-773 S4 — the tenant's first-admin invitation rows (admin-service's own record of «관리자 대기»). */
    private final OperatorInvitationPort invitationPort;
    /** TASK-MONO-773 S4 — «정리» cancels the pending first-admin invitation through the 772 cancel. */
    private final OperatorInvitationWriter invitationWriter;
    /** TASK-MONO-773 S4 — the recovery PATCH (PENDING → ACTIVE once the first admin accepted). */
    private final TenantActivation tenantActivation;

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

        // Validate status enum if provided (PENDING is never a request value — 773: only POST makes it)
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

        // --- TASK-MONO-773 S4: the first-admin gates, judged BEFORE anything is written ---
        if (statusChanged && targetStatus == TenantStatusEnum.ACTIVE) {
            List<InvitationView> firstAdminRows = invitationPort.findFirstAdminInvitations(List.of(tenantId));
            boolean accepted = firstAdminRows.stream().anyMatch(i -> "ACCEPTED".equals(i.status()));
            if (PENDING.equals(current.status())) {
                if (!accepted) {
                    throw TenantStatusConflictException.firstAdminPending(tenantId);
                }
                // The recovery path (773 AC-0 R4): the first admin's rows are committed; only the activation is
                // missing. Same activation as the acceptance's, actor = this SUPER_ADMIN.
                TenantSummary activated = tenantActivation.activate(tenantId, operator,
                        TenantActivation.TRIGGER_OPERATOR_COMPLETED, Permission.TENANT_MANAGE,
                        reason != null ? reason : "<not_provided>",
                        idempotencyKey != null ? idempotencyKey + ":status" : null);
                if (!displayNameChanged) {
                    return activated;
                }
                return updateDisplayNameOnly(tenantId, current.displayName(), newDisplayName, operator, reason,
                        idempotencyKey, Instant.now());
            }
            if (!firstAdminRows.isEmpty() && !accepted) {
                // A tenant cleaned up before any first admin existed (OD-2): re-activating it would make an ACTIVE
                // tenant with no admin. Tenants without first-admin rows (pre-773 · self-service) are not affected.
                throw TenantStatusConflictException.firstAdminPending(tenantId);
            }
        }
        if (statusChanged && targetStatus == TenantStatusEnum.SUSPENDED && PENDING.equals(current.status())) {
            cancelPendingFirstAdminInvitations(tenantId, operator, reason);
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
                    // TASK-MONO-773 S4 — the status really left (PENDING for «정리»), no longer a constant.
                    tenantEventPublisher.publishTenantSuspended(
                            tenantId, current.status(), operator.operatorId(), reason, now);
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
        return updateDisplayNameOnly(tenantId, current.displayName(), newDisplayName, operator, reason,
                idempotencyKey, now);
    }

    private TenantSummary updateDisplayNameOnly(String tenantId, String fromDisplayName, String newDisplayName,
                                                OperatorContext operator, String reason, String idempotencyKey,
                                                Instant now) {
        String auditId = auditor.newAuditId();
        auditor.recordStart(new AdminActionAuditor.StartRecord(
                auditId, ActionCode.TENANT_UPDATE, operator,
                "TENANT", tenantId,
                reason != null ? reason : "<not_provided>",
                null, idempotencyKey != null ? idempotencyKey : "dn:" + auditId,
                now, tenantId));

        try {
            TenantSummary updated = provisioningPort.update(tenantId, newDisplayName.trim(), null);

            auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                    auditId, ActionCode.TENANT_UPDATE, operator,
                    "TENANT", tenantId,
                    reason != null ? reason : "<not_provided>",
                    null, idempotencyKey != null ? idempotencyKey : "dn:" + auditId,
                    Outcome.SUCCESS, null, now, Instant.now()));

            tenantEventPublisher.publishTenantUpdated(
                    tenantId, fromDisplayName, newDisplayName.trim(),
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

    /**
     * «정리» step ① — every PENDING first-admin invitation of the tenant (at most one — V0055) → CANCELLED, each in
     * its own committed transaction with its {@code OPERATOR_INVITATION_CANCEL} row (the 772 cancel, actor = this
     * operator). None left (a retry after a failed suspend, or a tenant whose compensation left no invitation) →
     * no-op.
     */
    private void cancelPendingFirstAdminInvitations(String tenantId, OperatorContext operator, String reason) {
        for (InvitationView inv : invitationPort.findFirstAdminInvitations(List.of(tenantId))) {
            if (PENDING.equals(inv.status())) {
                invitationWriter.cancel(operator, inv, reason != null ? reason : "<not_provided>");
            }
        }
    }
}

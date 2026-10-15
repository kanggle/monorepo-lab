package com.example.admin.application.tenant;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.Outcome;
import com.example.admin.application.event.TenantEventPublisher;
import com.example.admin.application.port.TenantProvisioningPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * TASK-MONO-773 S4 — {@code PENDING → ACTIVE}, the ONLY way a {@code SUPER_ADMIN}-created tenant opens
 * (admin-api.md § Status 전이 매트릭스 · auth-to-admin.md § accept «첫 관리자 초대일 때» 9 · 10).
 *
 * <p>🔴 <b>Callers invoke this only after the first admin's rows are committed</b> (ADR-044 D3 — «관리자 먼저 ·
 * 활성화 나중»): the acceptance after its writer transaction returned; the recovery PATCH only when the first-admin
 * invitation is already {@code ACCEPTED}. That order is what {@code TenantFirstAdminIntegrationTest}'s
 * {@code ActivationProbe} measures.
 *
 * <ol>
 *   <li>(9) account-service {@code PATCH /internal/tenants/{id} status=ACTIVE} — a failure propagates
 *       ({@code 503}); nothing of step 10 is written and the tenant stays «관리자 있는 대기» (safe; retried);</li>
 *   <li>(10) one short admin transaction: {@code TENANT_ACTIVATE} audit + outbox {@code tenant.activated}. A failure
 *       here is logged ({@code tenant_activate_audit_failed}) and NOT rethrown — step 9 already happened; the
 *       accepted loss (2026-10-15 UTC, 773 S1 open question 5: no service consumer).</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantActivation {

    public static final String TRIGGER_FIRST_ADMIN_ACCEPTED = "FIRST_ADMIN_ACCEPTED";
    public static final String TRIGGER_OPERATOR_COMPLETED = "OPERATOR_COMPLETED";

    private final TenantProvisioningPort provisioningPort;
    private final Recorder recorder;

    /**
     * @param actor          the audit actor — the first admin (acceptance) or the {@code SUPER_ADMIN} (recovery)
     * @param permissionUsed {@code <self_invitation_accept>} for the acceptance, {@code tenant.manage} for the PATCH
     */
    public TenantSummary activate(String tenantId, OperatorContext actor, String trigger, String permissionUsed,
                                  String reason, String idempotencyKey) {
        TenantSummary updated = provisioningPort.update(tenantId, null, "ACTIVE");
        try {
            recorder.record(tenantId, actor, trigger, permissionUsed, reason, idempotencyKey);
        } catch (RuntimeException e) {
            log.warn("tenant_activate_audit_failed (tenant={}, trigger={}): {}", tenantId, trigger,
                    e.getClass().getSimpleName());
        }
        return updated;
    }

    /** Step 10 — its own bean so the {@code @Transactional} boundary is a real proxy call. */
    @Component
    @RequiredArgsConstructor
    public static class Recorder {

        private final AdminActionAuditor auditor;
        private final TenantEventPublisher tenantEventPublisher;

        @Transactional
        public void record(String tenantId, OperatorContext actor, String trigger, String permissionUsed,
                           String reason, String idempotencyKey) {
            Instant now = Instant.now();
            auditor.recordWithPermission(new AdminActionAuditor.AuditRecord(
                    auditor.newAuditId(), ActionCode.TENANT_ACTIVATE, actor,
                    "TENANT", tenantId,
                    reason, null,
                    idempotencyKey != null ? idempotencyKey : "tenant-activate:" + tenantId,
                    Outcome.SUCCESS, "trigger=" + trigger,
                    now, Instant.now(), tenantId),
                    permissionUsed);
            tenantEventPublisher.publishTenantActivated(tenantId, trigger, actor.operatorId(), now);
        }
    }
}

package com.example.admin.application;

import com.example.admin.application.port.TenantDomainSubscriptionPort;
import com.example.admin.application.tenant.SubscriptionMutationSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * TASK-BE-343 (ADR-MONO-023 § 3.3 step 2b — D3): the operator-facing
 * subscription management use-case. admin-service is the IAM plane — it gates the
 * action with {@code subscription.manage} (at the controller) + records the
 * operator audit here, then DELEGATES the entitlement write to account-service
 * (the entitlement authority, D3-A). account-service performs the state-machine
 * guard + the {@code tenant.subscription.changed} event (step 2a).
 *
 * <p>Plane separation (ADR-023 D2): the authorization decision is made in the IAM
 * plane (using IAM data — RBAC), the entitlement write happens in the entitlement
 * plane (account-service). account-service never reads IAM.
 *
 * <p>Audit is recorded after a successful delegation (mirrors
 * {@code PatchOperatorRoleUseCase} single-shot record-on-success). A failed
 * delegation propagates the downstream exception and writes no audit row — there
 * was no entitlement change to attribute. No {@code @Transactional}: the remote
 * call must not hold a DB transaction across network I/O, and the auditor manages
 * its own persistence (as it does for the aspect's deny path).
 */
@Service
@RequiredArgsConstructor
public class ManageSubscriptionUseCase {

    private final TenantDomainSubscriptionPort subscriptionPort;
    private final AdminActionAuditor auditor;

    public SubscriptionMutationSummary subscribe(String tenantId, String domainKey,
                                                 OperatorContext actor, String reason) {
        String actorId = actor == null ? null : actor.operatorId();
        SubscriptionMutationSummary result = subscriptionPort.subscribe(tenantId, domainKey, reason, actorId);
        recordAudit(ActionCode.SUBSCRIPTION_SUBSCRIBE, tenantId, domainKey, actor, reason);
        return result;
    }

    public SubscriptionMutationSummary changeStatus(String tenantId, String domainKey,
                                                    String targetStatus, OperatorContext actor, String reason) {
        String actorId = actor == null ? null : actor.operatorId();
        SubscriptionMutationSummary result =
                subscriptionPort.changeStatus(tenantId, domainKey, targetStatus, reason, actorId);
        recordAudit(ActionCode.SUBSCRIPTION_CHANGE_STATUS, tenantId, domainKey, actor, reason);
        return result;
    }

    private void recordAudit(ActionCode code, String tenantId, String domainKey,
                             OperatorContext actor, String reason) {
        String auditId = auditor.newAuditId();
        Instant now = Instant.now();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId,
                code,
                actor,
                "SUBSCRIPTION",
                tenantId + ":" + domainKey,
                AuditReasons.normalize(reason),
                null,
                "subscription:" + auditId,
                Outcome.SUCCESS,
                null,
                now,
                Instant.now()));
    }
}

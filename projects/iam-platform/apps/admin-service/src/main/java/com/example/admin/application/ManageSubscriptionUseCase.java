package com.example.admin.application;

import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.TenantDomainSubscriptionPort;
import com.example.admin.application.tenant.SubscriptionMutationSummary;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.Permission;
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
    private final TenantScopeGuard tenantScopeGuard;

    public SubscriptionMutationSummary subscribe(String tenantId, String domainKey,
                                                 OperatorContext actor, String reason) {
        // TASK-BE-614: the consumer-pool tenant is storage — entitlements on it would only ever
        // feed a token whose tenant_id is the pool value, which must never be minted.
        if (AdminOperator.isConsumerPool(tenantId)) {
            throw new TenantScopeDeniedException(
                    "tenant '" + tenantId + "' is reserved and cannot subscribe to a domain");
        }
        // TASK-MONO-750 (ADR-MONO-079 D4-A, rider R3): the `fan` domain derives FAN_OPERATOR at
        // assume-tenant. Only `fan-platform` may hold it — whose subscription.manage scope only a
        // platform-scope grant reaches — so the subscription is selectable by platform operators
        // only. Refused for every actor before the scope gate, like the consumer-pool refusal.
        if (AdminOperator.isReservedFanSubscription(tenantId, domainKey)) {
            throw new TenantScopeDeniedException(
                    "domain '" + domainKey + "' may be subscribed by tenant '"
                            + AdminOperator.FAN_PLATFORM_TENANT_ID + "' only");
        }
        // ADR-MONO-024 D2 + D5-C (TASK-BE-345): the confinement gate covers the
        // entitlement admin surface too. Net-zero for SUPER_ADMIN ('*'); denies
        // before the account-service delegation when out of scope.
        tenantScopeGuard.requireTenantInScope(
                actor, Permission.SUBSCRIPTION_MANAGE, tenantId, ActionCode.SUBSCRIPTION_SUBSCRIBE);
        String actorId = actor == null ? null : actor.operatorId();
        SubscriptionMutationSummary result = subscriptionPort.subscribe(tenantId, domainKey, reason, actorId);
        recordAudit(ActionCode.SUBSCRIPTION_SUBSCRIBE, tenantId, domainKey, actor, reason);
        return result;
    }

    public SubscriptionMutationSummary changeStatus(String tenantId, String domainKey,
                                                    String targetStatus, OperatorContext actor, String reason) {
        tenantScopeGuard.requireTenantInScope(
                actor, Permission.SUBSCRIPTION_MANAGE, tenantId, ActionCode.SUBSCRIPTION_CHANGE_STATUS);
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

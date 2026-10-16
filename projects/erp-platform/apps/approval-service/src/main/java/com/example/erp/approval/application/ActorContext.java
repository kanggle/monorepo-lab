package com.example.erp.approval.application;

import java.util.Set;

/**
 * Authenticated caller context built from the validated JWT. Value object —
 * keeps Spring Security types out of the application layer (mirrors
 * masterdata-service's {@code ActorContext}).
 *
 * <p>{@code actorId} = JWT {@code sub} — the IAM account UUID, i.e. who logged in. It is
 * NOT a person field value: since approval-api.md § v2.4 (TASK-MONO-776) the submitter /
 * approver / delegator is the employee linked to this {@code sub}, resolved per use case
 * ({@code ActingEmployee}); {@code actorId} stays on {@code audit_log.actor}. Roles/scopes ({@code erp.read} / {@code erp.write}
 * / {@code erp.approval.*}) and {@code org_scope} are extracted by the JWT
 * converter. {@code "*"} in {@code dataScopeDepartmentIds} = platform-wide scope.
 *
 * <p>{@code entitledDomains} carries the signed {@code entitled_domains} claim
 * (ADR-MONO-019 § D5). Since TASK-MONO-793 it no longer grants anything here — business roles decide
 * ({@link #canReadErp()} / {@link #canWriteErp()}); the tenant gate still reads the claim.
 */
public record ActorContext(String actorId, String tenantId, Set<String> roles,
                           Set<String> dataScopeDepartmentIds,
                           Set<String> entitledDomains) {

    public ActorContext(String actorId, String tenantId, Set<String> roles,
                        Set<String> dataScopeDepartmentIds) {
        this(actorId, tenantId, roles, dataScopeDepartmentIds, Set.of());
    }

    public boolean hasScope(String scope) {
        return roles != null && roles.contains(scope);
    }

    public boolean isPlatformScope() {
        return dataScopeDepartmentIds != null && dataScopeDepartmentIds.contains("*");
    }

    public boolean isOperator() {
        return hasScope("ERP_OPERATOR") || hasScope("ERP_ADMIN") || hasScope("SUPER_ADMIN");
    }

    /**
     * Entitlement-trust check (ADR-MONO-019 § D5): {@code true} iff the signed
     * {@code entitled_domains} claim contains {@code domain}. Fail-closed on any
     * shape anomaly. Grants READ only.
     */
    public boolean isEntitledTo(String domain) {
        return domain != null && entitledDomains != null && entitledDomains.contains(domain);
    }

    /**
     * Approval WRITE/transition — TASK-MONO-793 (ADR-MONO-083 P6; erp iam-integration.md § 업무 역할):
     * {@code ERP_APPROVAL_WRITE ∨ erp.write}. Before P6 this was «{@link #isOperator()} ∨ erp.write ∨
     * erp.approval.*»: the entry role {@code ERP_OPERATOR} is now derived for every ERP set holder (so it would
     * open approval to a masterdata-only operator) and the {@code erp.approval.*} scopes are issued nowhere.
     * Approver eligibility (separation of duties) stays the aggregate's guard, independent of this.
     */
    public boolean canWriteErp() {
        return hasScope("ERP_APPROVAL_WRITE") || hasScope("erp.write");
    }

    /**
     * Approval READ — TASK-MONO-793: {@code ERP_APPROVAL_READ ∨} the WRITE set {@code ∨ erp.read}. The
     * subscription ({@code entitled_domains ∋ erp}, ADR-MONO-019 entitlement-trust) no longer opens it — every
     * assume token carries the tenant's subscriptions, so it opened approval to operators holding no ERP set
     * (owner decision Q1). {@link #isEntitledTo(String)} stays for the record; no verdict reads it.
     */
    public boolean canReadErp() {
        return hasScope("ERP_APPROVAL_READ")
                || canWriteErp()
                || hasScope("erp.read");
    }
}

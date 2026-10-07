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
 * (ADR-MONO-019 § D5). It grants READ visibility to an entitled domain even
 * without an {@code erp.read}/operator role — it NEVER widens a WRITE/transition.
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

    /** The erp domain the entitlement-trust READ grant applies to (ADR-MONO-019 § D5). */
    private static final String ENTITLED_DOMAIN = "erp";

    /**
     * The erp WRITE/transition scope-tuple (extracted dedup — net-zero). WRITE is
     * operator OR one of the write/approval scopes. Entitlement-trust NEVER widens
     * a WRITE (architecture.md § Approver authorization).
     */
    public boolean canWriteErp() {
        return isOperator()
                || hasScope("erp.write")
                || hasScope("erp.approval.create")
                || hasScope("erp.approval.approve");
    }

    /**
     * The erp READ scope-tuple (extracted dedup — net-zero). READ is the WRITE-set
     * OR {@code erp.read} OR entitlement-trust to the erp domain (ADR-MONO-019 —
     * entitlement grants READ only).
     */
    public boolean canReadErp() {
        return canWriteErp()
                || hasScope("erp.read")
                || isEntitledTo(ENTITLED_DOMAIN);
    }
}

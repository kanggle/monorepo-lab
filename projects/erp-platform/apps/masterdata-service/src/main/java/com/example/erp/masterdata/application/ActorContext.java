package com.example.erp.masterdata.application;

import java.util.Set;

/**
 * Authenticated caller context built from the validated JWT. Value object —
 * keeps Spring Security types out of the application layer.
 *
 * <p>Roles are normalized to upper-case strings; common erp roles are
 * {@code ERP_OPERATOR}, {@code ERP_ADMIN}, {@code SUPER_ADMIN}. Scope claims
 * ({@code erp.read} / {@code erp.write}) are extracted by the JWT converter.
 * The {@code dataScopeDepartmentIds} set holds the department ids the actor
 * may read/write under; an empty set + non-wildcard is the fail-closed denial
 * default.
 *
 * <p>{@code "*"} in {@code dataScopeDepartmentIds} = platform-wide scope (used
 * by {@code client_credentials} machine tokens per architecture.md §
 * Authorization matrix point 3).
 *
 * <p>{@code entitledDomains} carries the signed {@code entitled_domains} claim
 * (ADR-MONO-019 § D5 entitlement-trust). It is extracted from the verified JWT
 * by the converter and used by the authorization layer to grant <b>READ</b>
 * visibility to a domain the caller is entitled to even without an
 * {@code erp.read}/operator role — mirroring the tenant gate's
 * {@code TenantClaimValidator.isEntitled} dual-accept. It NEVER widens WRITE.
 */
public record ActorContext(String actorId, String tenantId, Set<String> roles,
                            Set<String> dataScopeDepartmentIds,
                            Set<String> entitledDomains) {

    /**
     * Backward-compatible constructor for callers (and existing tests) that
     * predate the {@code entitled_domains} entitlement-trust claim. Defaults
     * {@code entitledDomains} to the empty set (fail-closed: no entitlement).
     */
    public ActorContext(String actorId, String tenantId, Set<String> roles,
                        Set<String> dataScopeDepartmentIds) {
        this(actorId, tenantId, roles, dataScopeDepartmentIds, Set.of());
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasScope(String scope) {
        return roles != null && roles.contains(scope);
    }

    public boolean isPlatformScope() {
        return dataScopeDepartmentIds != null
                && dataScopeDepartmentIds.contains("*");
    }

    public boolean isOperator() {
        return hasRole("ERP_OPERATOR") || hasRole("ERP_ADMIN")
                || hasRole("SUPER_ADMIN");
    }

    /**
     * Entitlement-trust check (ADR-MONO-019 § D5): {@code true} iff the signed
     * {@code entitled_domains} claim contains {@code domain}. Fail-closed on
     * any shape anomaly (null set / null arg) — mirrors
     * {@code TenantClaimValidator.isEntitled}. Grants READ only; the
     * authorization adapter never consults this for WRITE.
     */
    public boolean isEntitledTo(String domain) {
        return domain != null && entitledDomains != null
                && entitledDomains.contains(domain);
    }
}

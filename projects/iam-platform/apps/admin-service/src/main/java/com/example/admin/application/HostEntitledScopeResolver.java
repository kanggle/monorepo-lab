package com.example.admin.application;

import com.example.admin.domain.rbac.ScopeSet;

import java.util.Optional;

/**
 * TASK-BE-477 / ADR-MONO-045 D3 — resolves the {@code {domains}×{roles}} a HOST
 * tenant actually holds, so the cross-org derivation can re-intersect
 * ({@code delegated_scope ∩ participant_scope ∩ host-holds}) at request time (the
 * runtime double-defense of the invite-time ≤-own cap) and the invite-time cap can
 * reject a {@code delegatedScope} that exceeds what the host holds.
 *
 * <p><b>Deferred seam.</b> admin-service is a command gateway and owns no local,
 * authoritative mirror of a tenant's held domains/roles (domain entitlements live in
 * account-service; the assume-tenant issuance path forbids a hot-path cross-service
 * callback — ADR-020 § 3.1). The default {@link UnboundedHostEntitledScopeResolver}
 * therefore returns {@link Optional#empty()} = "unbounded" (host holds ⊇ delegated),
 * so the request-time {@code ∩ host-holds} defers entirely to the invite-time cap
 * (which IS enforced: no admin role in {@code delegatedScope}). A later task (ADR-045
 * §3.4 step 2b/4, aligned with the ABAC/entitlement-plane deferral) can supply a real
 * host-holds resolver WITHOUT changing the triple-intersection algorithm.
 */
public interface HostEntitledScopeResolver {

    /**
     * @param hostTenantId the host tenant whose held scope is being resolved
     * @return the host's held {@code {domains, roles}}, or {@link Optional#empty()}
     *         to mean "unbounded" (no request-time clip — trust the invite-time cap)
     */
    Optional<ScopeSet> resolve(String hostTenantId);
}

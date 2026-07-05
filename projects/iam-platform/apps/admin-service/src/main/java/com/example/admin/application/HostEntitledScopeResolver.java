package com.example.admin.application;

import com.example.admin.domain.rbac.ScopeSet;

import java.util.Optional;

/**
 * TASK-BE-477 / ADR-MONO-045 D3 — the {@code host-holds} term of the REQUEST-TIME
 * cross-org derivation ({@code delegated_scope ∩ participant_scope ∩ host-holds} in
 * {@link OperatorAssignmentCheckUseCase}).
 *
 * <p><b>Intentionally unbounded at request time (TASK-BE-479 — final decision, no
 * longer a deferred stub).</b> The default {@link UnboundedHostEntitledScopeResolver}
 * returns {@link Optional#empty()} = "unbounded" ({@code ∩ host-holds} is the
 * identity), and this is the CORRECT end state — not a placeholder — for two reasons:
 * <ol>
 *   <li><b>ADR-MONO-020 §3.1</b> forbids a cross-service callback on the assume-tenant
 *       issuance hot path, and the authoritative host holdings live in account-service
 *       (domain entitlements) — so a real host-holds fetch cannot run here.</li>
 *   <li><b>Redundant with step 2b (TASK-BE-478).</b> The assume-tenant token mint
 *       already clips the domain dimension to the host's live entitlements
 *       ({@code entitled_domains = host-ACTIVE ∩ delegated.domains}); a delegated role
 *       for a domain the host no longer holds is therefore inert (the domain gateway
 *       403s it). A request-time role re-clip would add nothing.</li>
 * </ol>
 *
 * <p>The real ≤-own enforcement lives at <b>invite time</b> (a COMMAND path where the
 * cross-service read IS permitted): {@code PartnershipManagementUseCase} rejects a
 * {@code delegatedScope} whose domain is not in the host's ACTIVE subscriptions or
 * whose role is not an operator-tier {@code DelegatableRoleCatalog} role. This seam
 * remains so a future local host-holds mirror could bound the request path too, WITHOUT
 * changing the triple-intersection algorithm — but that is not needed for correctness.
 */
public interface HostEntitledScopeResolver {

    /**
     * @param hostTenantId the host tenant whose held scope is being resolved
     * @return the host's held {@code {domains, roles}}, or {@link Optional#empty()}
     *         to mean "unbounded" (no request-time clip — trust the invite-time cap)
     */
    Optional<ScopeSet> resolve(String hostTenantId);
}

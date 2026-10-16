package com.example.account.application.service;

import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.domain.orgnode.EntitlementCeiling;
import com.example.account.domain.repository.TenantDomainSubscriptionRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantDomainSubscription;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TASK-BE-491 (ADR-MONO-047 § D6): <b>the single point at which the org-node entitlement
 * ceiling is enforced.</b>
 *
 * <p>Returns a tenant's <b>effective</b> entitled domains:
 * {@code ACTIVE subscriptions ∩ effectiveCeiling(tenant)}, preserving the ACTIVE list's
 * order.
 *
 * <p>Why here and nowhere else:
 * <ul>
 *   <li>account-service owns {@code tenants}, hence {@code org_node}. Intersecting at the
 *       source makes <i>"prove this tenant cannot reach domain X"</i> a one-query property
 *       rather than an ancestor-chain walk scattered across services.</li>
 *   <li>auth-service's {@code TenantClaimTokenCustomizer} stays byte-unchanged: it consumes
 *       {@code AccountServicePort.listEntitledDomains(tenantId)} and derives roles from
 *       whatever it receives. ADR-035 derivation is domain-keyed and per-domain, so
 *       {@code derive(E ∩ C) = derive(E) ∩ derive(C)} — D6's stated composition holds
 *       exactly.</li>
 *   <li>ADR-045's cross-org {@code applyCrossOrgCap} narrows on top, order-independently
 *       (both gates only narrow).</li>
 * </ul>
 *
 * <p>This is deliberately NOT the same read as
 * {@link TenantDomainSubscriptionQueryUseCase#listActive}: that one returns the raw ACTIVE
 * rows and backs the console catalog and subscription management, which must keep seeing
 * what is stored. Narrowing it would make a ceiling look like a delete to the operator who
 * has to manage the subscription.
 *
 * <p><b>Fail-soft is preserved by the caller, not weakened here</b>: if this call fails,
 * auth-service omits the {@code entitled_domains} claim entirely and the domain gateway
 * 403s. A failure can never <i>widen</i> reach.
 */
@Service
@RequiredArgsConstructor
public class TenantEntitledDomainsQueryUseCase {

    private final TenantRepository tenantRepository;
    private final TenantDomainSubscriptionRepository subscriptionRepository;
    private final OrgNodeQueryUseCase orgNodeQueryUseCase;

    /**
     * The effective entitled domain keys only — {@link #entitlement(String)}{@code .domainKeys()}.
     *
     * @throws TenantNotFoundException the tenant is not registered (404)
     */
    @Transactional(readOnly = true)
    public List<String> effectiveEntitledDomains(String tenantId) {
        return entitlement(tenantId).domainKeys();
    }

    /**
     * TASK-BE-639 (owner decision 2026-10-16 UTC, Q1 = B) — the effective entitled domains AND the tenant's own
     * status, from the one read the assume path already makes. A tenant that is not {@code ACTIVE}
     * ({@code PENDING} · {@code SUSPENDED}) is entitled to nothing, whatever its subscription rows say: the keys are
     * {@code []}. auth-service refuses the operator assume exchange on the status; the empty keys are the
     * old-auth-image safety (a caller that ignores {@code tenantStatus} still gets no domain reach).
     *
     * @throws TenantNotFoundException the tenant is not registered (404)
     */
    @Transactional(readOnly = true)
    public Entitlement entitlement(String tenantId) {
        Tenant tenant = tenantRepository.findById(new TenantId(tenantId))
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        String status = tenant.getStatus().name();
        if (!tenant.isActive()) {
            return new Entitlement(List.of(), status);
        }
        // An ungrouped tenant (org_node_id = NULL) resolves to UNBOUNDED — the intersection
        // identity — so the filter below is a no-op and the output is byte-identical to the
        // pre-ADR-047 behaviour (D7 net-zero).
        EntitlementCeiling ceiling = orgNodeQueryUseCase.effectiveCeilingForTenant(tenantId);

        List<String> keys = subscriptionRepository.findActiveByTenantId(tenantId).stream()
                .map(TenantDomainSubscription::getDomainKey)
                .filter(ceiling::permits)
                .toList();
        return new Entitlement(keys, status);
    }

    /** TASK-BE-639 — the effective keys + the tenant's status ({@code ACTIVE} | {@code PENDING} | {@code SUSPENDED}). */
    public record Entitlement(List<String> domainKeys, String tenantStatus) {}
}

package com.example.admin.application.console;

import com.example.admin.application.OperatorContext;
import com.example.admin.application.TenantScopeResolver;
import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.tenant.ListTenantDomainSubscriptionsUseCase;
import com.example.admin.application.tenant.ListTenantsUseCase;
import com.example.admin.application.tenant.TenantDomainSubscriptionSummary;
import com.example.admin.application.tenant.TenantPageSummary;
import com.example.admin.application.tenant.TenantSummary;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TASK-BE-296: Builds the platform-console product/tenant registry for the
 * authenticated operator.
 *
 * <p>Read-only — no audit row (consistent with {@code GetTenantUseCase} /
 * {@code ListTenantsUseCase} read-path policy; admin-service does not host
 * domain state, this is a projection of existing tenant/product metadata).
 *
 * <p>Authoritative shape + rules:
 * {@code specs/contracts/http/console-registry-api.md} and
 * {@code specs/features/multi-tenancy.md} "Platform Console". The 5-product
 * catalog is fixed (ADR-MONO-013 federated domains). {@code available} and the
 * per-product tenant binding are derived here.
 *
 * <p>TASK-BE-322 (ADR-MONO-019 D4): a domain product's {@code tenants} binding
 * is now derived from the ACTIVE tenant↔domain subscriptions account-service
 * owns (ADR-019 D2 entitlement authority), read via
 * {@link ListTenantDomainSubscriptionsUseCase}, instead of the prior fixed
 * {@code tenantSlug == domain} binding. The response envelope is unchanged
 * (zero console-web change); the backward-compatible seed keeps the values
 * byte-identical in step 1.
 *
 * <p>Multi-tenant isolation ({@code rules/traits/multi-tenant.md} M3/M6): a
 * single-tenant operator NEVER sees another tenant's slug in any product's
 * {@code tenants} array. The {@code gap} product binds to ALL registered
 * tenants for a platform-scope operator but only the operator's own tenant for
 * a single-tenant operator.
 */
@Service
@RequiredArgsConstructor
public class ConsoleRegistryUseCase {

    /** account-service list page size cap (admin-api.md / contract: max 100). */
    private static final int TENANT_PAGE_SIZE = 100;
    /** Defensive bound on pages walked; 5 products × portfolio scale is tiny. */
    private static final int MAX_TENANT_PAGES = 50;
    private static final String ACTIVE = "ACTIVE";

    private final AdminOperatorJpaRepository operatorRepository;
    private final ListTenantsUseCase listTenantsUseCase;
    private final ListTenantDomainSubscriptionsUseCase listSubscriptionsUseCase;
    private final TenantScopeResolver tenantScopeResolver;

    public ConsoleRegistry execute(OperatorContext operator) {
        // TASK-BE-326: resolveOperator() reads the JPA entity directly (bypasses
        // OperatorLookupPort). We capture the entity here so the dual-read
        // resolver gets the internal BIGINT id + home tenant (divergence risk #1
        // — the registry path is wired explicitly).
        AdminOperatorJpaEntity entity = operatorRepository.findByOperatorId(operator.operatorId())
                .orElseThrow(() -> new OperatorUnauthorizedException(
                        "Operator not found: " + operator.operatorId()));
        AdminOperator adminOperator = toDomain(entity);

        // Effective tenant scope: assignment rows ∪ {home tenant}. NET-ZERO with
        // no assignments → {home tenant} → byte-identical legacy binding.
        Set<String> effectiveTenants = tenantScopeResolver
                .resolveEffectiveTenantScope(entity.getId(), entity.getTenantId());

        // Registered, ACTIVE tenant slugs (account-service owned). A SUSPENDED
        // or unregistered tenant is excluded by construction.
        List<String> activeTenants = activeRegisteredTenantSlugs();

        // TASK-BE-322 (ADR-MONO-019 D2/D4): ACTIVE tenant↔domain subscriptions,
        // grouped domainKey → tenant ids. account-service is the entitlement
        // authority; a domain product binds to the tenants subscribed to its
        // domain_key (replaces the prior fixed `tenantSlug == domain` binding).
        // Fetched once per request (same as activeTenants).
        Map<String, Set<String>> subscriptionsByDomain = subscriptionsByDomain();

        boolean platformScope = adminOperator.isPlatformScope();

        List<ConsoleProduct> products = new ArrayList<>(ProductCatalog.entries().size());
        for (ProductCatalog.Entry entry : ProductCatalog.entries()) {
            List<String> tenants = entry.available()
                    ? selectableTenants(entry, platformScope, effectiveTenants, activeTenants,
                            subscriptionsByDomain)
                    : List.of();
            products.add(new ConsoleProduct(
                    entry.productKey(),
                    entry.displayName(),
                    entry.available(),
                    tenants,
                    entry.baseRoute(),
                    operatorContextFor(entry, adminOperator)));
        }
        return new ConsoleRegistry(products);
    }

    /**
     * TASK-BE-304: per-product per-operator profile attributes emission rule.
     *
     * <p>v1: only the {@code finance} product item populates
     * {@code operatorContext}, using
     * {@code admin_operators.finance_default_account_id} when the column is
     * non-null and non-empty after trim. All other 4 products always return
     * {@code null} (the response DTO omits {@code operatorContext} via
     * {@code @JsonInclude(Include.NON_NULL)}).
     *
     * <p>Authoritative emission rule:
     * {@code specs/contracts/http/console-registry-api.md
     * § Per-operator profile attributes (operatorContext)} per-product table.
     */
    private ProductOperatorContext operatorContextFor(ProductCatalog.Entry entry,
                                                       AdminOperator adminOperator) {
        if (!"finance".equals(entry.productKey())) {
            return null;
        }
        String accountId = adminOperator.financeDefaultAccountId();
        if (!StringUtils.hasText(accountId)) {
            return null;
        }
        return new ProductOperatorContext(accountId);
    }

    /**
     * Tenant selection = intersection of
     * (1) the product's tenant binding,
     * (2) the operator's tenant scope,
     * (3) the tenant being registered + ACTIVE.
     *
     * <p>{@code gap} binds to all registered tenants (the platform federates
     * them); a domain product binds to the tenants that hold an ACTIVE
     * subscription to its {@code productKey} (TASK-BE-322 / ADR-019 D4 — the
     * subscription read replaces the prior fixed {@code tenantSlug == domain}
     * binding).
     *
     * <p>net-zero (ADR-019 step 1): the backward-compatible seed makes each
     * domain-slug tenant subscribe to its own domain
     * ({@code (wms,wms),(scm,scm),(erp,erp),(finance,finance)}), so the
     * subscription set for {@code domainKey=wms} is {@code {wms}} and
     * {@code {wms} ∩ activeTenants} reproduces the old
     * {@code activeTenants.contains("wms") ? ["wms"] : []} binding exactly.
     */
    private List<String> selectableTenants(ProductCatalog.Entry entry,
                                            boolean platformScope,
                                            Set<String> effectiveTenants,
                                            List<String> activeTenants,
                                            Map<String, Set<String>> subscriptionsByDomain) {
        // (1) product binding
        List<String> bound;
        if (entry.bindsAllTenants()) {
            bound = activeTenants; // (1)∩(3): already ACTIVE+registered
        } else {
            // domain product → bound to the tenants subscribed to its
            // domain_key (productKey), intersected with registered+ACTIVE (3).
            // Preserve activeTenants ordering for deterministic output.
            Set<String> subscribed =
                    subscriptionsByDomain.getOrDefault(entry.productKey(), Set.of());
            List<String> intersection = new ArrayList<>();
            for (String tenant : activeTenants) {
                if (subscribed.contains(tenant)) {
                    intersection.add(tenant);
                }
            }
            bound = intersection;
        }

        // (2) operator scope
        if (platformScope) {
            return List.copyOf(bound);
        }
        // single-tenant operator: never expose a tenant outside the operator's
        // effective scope. TASK-BE-326 dual-read: bound ∩ effectiveTenants
        // (assignment rows ∪ home tenant), preserving bound ordering. NET-ZERO
        // with no assignments → effectiveTenants == {home tenant} → reproduces
        // the legacy `bound.contains(ownTenant) ? [ownTenant] : []` exactly.
        List<String> scoped = new ArrayList<>();
        for (String tenant : bound) {
            if (effectiveTenants.contains(tenant)) {
                scoped.add(tenant);
            }
        }
        return scoped;
    }

    /**
     * TASK-BE-322: groups the ACTIVE tenant↔domain subscriptions by domain key.
     * account-service owns the entitlement table (ADR-019 D2); a downstream
     * failure propagates as the same {@code DownstreamFailureException} →
     * {@code 503} the tenant list call already raises (no partial catalog).
     */
    private Map<String, Set<String>> subscriptionsByDomain() {
        Map<String, Set<String>> byDomain = new LinkedHashMap<>();
        for (TenantDomainSubscriptionSummary s : listSubscriptionsUseCase.execute()) {
            if (s == null || s.domainKey() == null || s.tenantId() == null) {
                continue;
            }
            byDomain.computeIfAbsent(s.domainKey(), k -> new LinkedHashSet<>())
                    .add(s.tenantId());
        }
        return byDomain;
    }

    private List<String> activeRegisteredTenantSlugs() {
        Set<String> slugs = new LinkedHashSet<>();
        int page = 0;
        while (page < MAX_TENANT_PAGES) {
            TenantPageSummary summary =
                    listTenantsUseCase.execute(ACTIVE, null, page, TENANT_PAGE_SIZE);
            if (summary == null || summary.items() == null || summary.items().isEmpty()) {
                break;
            }
            for (TenantSummary t : summary.items()) {
                if (t != null && ACTIVE.equalsIgnoreCase(t.status())) {
                    slugs.add(t.tenantId());
                }
            }
            page++;
            if (page >= summary.totalPages()) {
                break;
            }
        }
        return new ArrayList<>(slugs);
    }

    private AdminOperator toDomain(AdminOperatorJpaEntity e) {
        return new AdminOperator(
                e.getOperatorId(),
                e.getEmail(),
                e.getDisplayName(),
                AdminOperator.Status.valueOf(e.getStatus()),
                e.getVersion(),
                e.getTenantId(),
                e.getFinanceDefaultAccountId());
    }
}

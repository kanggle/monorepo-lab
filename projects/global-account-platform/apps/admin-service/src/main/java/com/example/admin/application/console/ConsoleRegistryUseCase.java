package com.example.admin.application.console;

import com.example.admin.application.OperatorContext;
import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.tenant.ListTenantsUseCase;
import com.example.admin.application.tenant.TenantPageSummary;
import com.example.admin.application.tenant.TenantSummary;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    public ConsoleRegistry execute(OperatorContext operator) {
        AdminOperator adminOperator = resolveOperator(operator);

        // Registered, ACTIVE tenant slugs (account-service owned). A SUSPENDED
        // or unregistered tenant is excluded by construction.
        List<String> activeTenants = activeRegisteredTenantSlugs();

        boolean platformScope = adminOperator.isPlatformScope();
        String ownTenant = adminOperator.tenantId();

        List<ConsoleProduct> products = new ArrayList<>(ProductCatalog.entries().size());
        for (ProductCatalog.Entry entry : ProductCatalog.entries()) {
            List<String> tenants = entry.available()
                    ? selectableTenants(entry, platformScope, ownTenant, activeTenants)
                    : List.of();
            products.add(new ConsoleProduct(
                    entry.productKey(),
                    entry.displayName(),
                    entry.available(),
                    tenants,
                    entry.baseRoute()));
        }
        return new ConsoleRegistry(products);
    }

    /**
     * Tenant selection = intersection of
     * (1) the product's tenant binding,
     * (2) the operator's tenant scope,
     * (3) the tenant being registered + ACTIVE.
     *
     * <p>{@code gap} binds to all registered tenants (the platform federates
     * them); a domain product binds to its own tenant slug.
     */
    private List<String> selectableTenants(ProductCatalog.Entry entry,
                                            boolean platformScope,
                                            String ownTenant,
                                            List<String> activeTenants) {
        // (1) product binding
        List<String> bound;
        if (entry.bindsAllTenants()) {
            bound = activeTenants; // (1)∩(3): already ACTIVE+registered
        } else {
            // domain product → bound to exactly its own tenant slug, only if
            // that tenant is registered + ACTIVE (3).
            bound = activeTenants.contains(entry.tenantSlug())
                    ? List.of(entry.tenantSlug())
                    : List.of();
        }

        // (2) operator scope
        if (platformScope) {
            return List.copyOf(bound);
        }
        // single-tenant operator: never expose another tenant's slug.
        return bound.contains(ownTenant) ? List.of(ownTenant) : List.of();
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

    private AdminOperator resolveOperator(OperatorContext operator) {
        return operatorRepository.findByOperatorId(operator.operatorId())
                .map(e -> new AdminOperator(
                        e.getOperatorId(),
                        e.getEmail(),
                        e.getDisplayName(),
                        AdminOperator.Status.valueOf(e.getStatus()),
                        e.getVersion(),
                        e.getTenantId()))
                .orElseThrow(() -> new OperatorUnauthorizedException(
                        "Operator not found: " + operator.operatorId()));
    }
}

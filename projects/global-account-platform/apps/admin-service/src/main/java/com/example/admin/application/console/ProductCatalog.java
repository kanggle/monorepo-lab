package com.example.admin.application.console;

import java.util.List;

/**
 * TASK-BE-296: The fixed platform-console product catalog (ADR-MONO-013
 * federated domains). Five product keys; {@code available} reflects whether
 * the domain is bootstrapped on the GAP platform.
 *
 * <p>Authoritative table:
 * {@code specs/contracts/http/console-registry-api.md} "Product catalog" and
 * {@code specs/features/multi-tenancy.md} "Platform Console". Adding a product
 * or flipping {@code available} is a registry change only — zero
 * {@code console-web} code change (console-integration-contract § 2.2 / D5).
 *
 * <p>Tenant binding:
 * <ul>
 *   <li>{@code gap} federates ALL registered tenants
 *       ({@link Entry#bindsAllTenants()} = true).</li>
 *   <li>{@code wms}/{@code scm} bind to their own tenant slug.</li>
 *   <li>{@code erp}/{@code finance} are V1 live (ADR-MONO-013 § D6 Phase 5/6
 *       COMPLETE 2026-05-19/20); both bind to their own tenant slug — same
 *       shape as wms/scm.</li>
 * </ul>
 */
public final class ProductCatalog {

    private ProductCatalog() {
    }

    /**
     * A catalog entry. {@code tenantSlug} is the tenant a domain product binds
     * to; ignored when {@code bindsAllTenants} is true (the {@code gap} case)
     * or when {@code available} is false.
     */
    public record Entry(
            String productKey,
            String displayName,
            boolean available,
            boolean bindsAllTenants,
            String tenantSlug,
            String baseRoute
    ) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("gap", "Global Account Platform", true, true, null, "/gap"),
            new Entry("wms", "Warehouse Management Platform", true, false, "wms", "/wms"),
            new Entry("scm", "Supply Chain Management Platform", true, false, "scm", "/scm"),
            new Entry("erp", "Enterprise Resource Planning", true, false, "erp", "/erp"),
            new Entry("finance", "Finance Platform", true, false, "finance", "/finance")
    );

    public static List<Entry> entries() {
        return ENTRIES;
    }
}

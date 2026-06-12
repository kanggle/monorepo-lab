package com.example.admin.application.console;

import java.util.List;

/**
 * TASK-BE-296: The fixed platform-console product catalog (ADR-MONO-013
 * federated domains + ADR-MONO-030 ecommerce marketplace). Six product keys;
 * {@code available} reflects whether the domain is bootstrapped on the IAM
 * platform.
 *
 * <p>Authoritative table:
 * {@code specs/contracts/http/console-registry-api.md} "Product catalog" and
 * {@code specs/features/multi-tenancy.md} "Platform Console".
 *
 * <p><b>Render is data-driven (0-change); membership is an explicit
 * extension.</b> Flipping {@code available} / {@code displayName} /
 * {@code tenants} of an EXISTING catalog member is a registry change only —
 * zero {@code console-web} code change (the catalog renders the dynamic product
 * list verbatim; console-integration-contract § 2.2 / D5). Adding a NEW
 * {@code productKey} here, however, ALSO requires a one-line consumer-side
 * {@code console-web} {@code ProductKeySchema} Zod enum extension
 * ({@code src/shared/api/registry-types.ts}) + its {@code registry-contract.test.ts}
 * membership assertion — the deliberate fixed-membership guard. An unknown
 * {@code productKey} makes the console's {@code RegistryResponseSchema.parse}
 * throw → the whole catalog renders degraded. So a new-domain addition lands
 * the producer entry below and the consumer enum in the SAME atomic PR
 * (TASK-MONO-240 added {@code ecommerce}; ADR-MONO-030 § 6 factual correction).
 *
 * <p>Tenant binding:
 * <ul>
 *   <li>{@code iam} federates ALL registered tenants
 *       ({@link Entry#bindsAllTenants()} = true).</li>
 *   <li>{@code wms}/{@code scm} bind to their own tenant slug.</li>
 *   <li>{@code erp}/{@code finance} are V1 live (ADR-MONO-013 § D6 Phase 5/6
 *       COMPLETE 2026-05-19/20); both bind to their own tenant slug — same
 *       shape as wms/scm.</li>
 *   <li>{@code ecommerce} is V1 live (ADR-MONO-030 ACCEPTED — multi-vendor
 *       marketplace SaaS); binds to its own tenant slug subscription-driven
 *       like wms/scm/erp/finance ({@code tenant_domain_subscription}
 *       {@code domain_key='ecommerce'} self-seed V0022).</li>
 * </ul>
 */
public final class ProductCatalog {

    private ProductCatalog() {
    }

    /**
     * A catalog entry. {@code tenantSlug} is the tenant a domain product binds
     * to; ignored when {@code bindsAllTenants} is true (the {@code iam} case)
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
            new Entry("iam", "Identity & Access Management", true, true, null, "/iam"),
            new Entry("wms", "Warehouse Management System", true, false, "wms", "/wms"),
            new Entry("scm", "Supply Chain Management", true, false, "scm", "/scm"),
            new Entry("erp", "Enterprise Resource Planning", true, false, "erp", "/erp"),
            new Entry("finance", "Finance", true, false, "finance", "/finance"),
            new Entry("ecommerce", "E-Commerce Marketplace", true, false, "ecommerce", "/ecommerce")
    );

    public static List<Entry> entries() {
        return ENTRIES;
    }
}

package com.kanggle.platformconsole.bff.application.port.outbound;

import java.util.Map;

/**
 * Narrow outbound port: ecommerce product catalog snapshot read for the
 * Operator Overview composition (§ 2.4.9.1 row 6, TASK-MONO-243 — ADR-MONO-030
 * Step 4 facet a-후속-2).
 *
 * <p>Mirrors {@link ErpDepartmentsReadPort} (same supertype, same method shape)
 * — a page-total snapshot read whose {@code totalElements} surfaces the tenant's
 * product count.
 *
 * <p><b>Distinct from</b> {@link EcommerceHealthReadPort} (§ 2.4.9.2): that leg
 * is a credential-LESS {@code /actuator/health} probe; this leg is
 * credential-FULL (IAM OIDC access token) + tenant pass-through against
 * {@code /api/admin/products}. Both reuse the {@code ecommerceRestClient}
 * (= {@code ecommerce.local}) but differ in path and authorization.
 */
public interface EcommerceOverviewReadPort extends DomainReadPort<Map<String, Object>> {
}

package com.example.scmplatform.inventoryvisibility.adapter.inbound.web;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Static utility for extracting the {@code tenant_id} claim from a JWT.
 *
 * <p>Shared by {@link InventoryVisibilityController} and {@link NodeStalenessController}
 * to eliminate byte-identical {@code extractTenantId} duplication
 * (TASK-SCM-BE-017 A3 — L6 reduce duplication).
 *
 * <p>Not a Spring bean. Lives in the same package as its consumers
 * ({@code adapter/inbound/web/}) alongside {@link filter.TenantClaimEnforcer}.
 *
 * <p>Behaviour is preserved byte-identical from the original private methods:
 * if {@code jwt} is {@code null} or the {@code tenant_id} claim is absent,
 * the default tenant {@code "scm"} is returned.
 */
public final class TenantClaimExtractor {

    private static final String DEFAULT_TENANT_ID = "scm";

    private TenantClaimExtractor() {
        // utility class — no instances
    }

    /**
     * Extracts the {@code tenant_id} claim from the given JWT.
     *
     * @param jwt the resolved JWT principal; may be {@code null} in tests or
     *            when the security filter chain is bypassed
     * @return the {@code tenant_id} claim value, or {@code "scm"} if absent
     */
    public static String extractTenantId(Jwt jwt) {
        if (jwt == null) return DEFAULT_TENANT_ID;
        String t = jwt.getClaimAsString("tenant_id");
        return t != null ? t : DEFAULT_TENANT_ID;
    }
}

package com.example.scmplatform.procurement.presentation.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Centralized whitelist of paths that bypass authentication AND tenant-claim
 * enforcement. Both {@code SecurityConfig} (Spring Security filter chain) and
 * {@code TenantClaimEnforcer} (defense-in-depth tenant gate) reference this
 * list so the two stay in lockstep.
 *
 * <p>Supplier webhooks intentionally do NOT bypass authentication — they go
 * through a dedicated {@code shared-secret} verification chain in v1
 * (see {@code SupplierAckWebhookController}). When v2 introduces HMAC-signed
 * webhooks, the verification logic is added to the webhook controllers, not
 * here.
 */
public final class PublicPaths {

    public static final Set<String> EXACT = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus"
    );

    public static final Set<String> PREFIXES = Set.of(
            "/actuator/health/",
            // Supplier webhooks authenticate via a shared-secret header (v1)
            // verified inside the webhook controllers themselves. Bearer tokens
            // would be incorrect here — supplier doesn't have an OIDC client.
            "/api/procurement/webhooks/"
    );

    private PublicPaths() {
    }

    public static boolean isPublic(String path) {
        if (path == null) return false;
        if (EXACT.contains(path)) return true;
        for (String prefix : PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    public static boolean isPublic(HttpServletRequest request) {
        return isPublic(request.getRequestURI());
    }
}

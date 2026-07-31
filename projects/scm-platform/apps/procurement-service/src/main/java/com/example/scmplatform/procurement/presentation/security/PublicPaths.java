package com.example.scmplatform.procurement.presentation.security;

import com.example.security.servlet.PublicPathSet;
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
 *
 * <p>The {@code EXACT}/{@code PREFIXES} matching mechanism delegates to the shared
 * {@link PublicPathSet} (ADR-MONO-058 § D5) — this class still owns the path data.
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

    /**
     * The same data as a {@link PublicPathSet}, for the ADR-MONO-058 § D4 chain assembler.
     *
     * <p>Handing {@code SecurityConfig} and {@code TenantClaimEnforcer} the <em>same</em> instance is
     * the point: the paths Spring Security permits unauthenticated and the paths the tenant gate
     * skips are then one object, and cannot drift apart (ADR-MONO-049 § 1.8).
     */
    public static final PublicPathSet AS_SET = PublicPathSet.of(EXACT, PREFIXES);

    private PublicPaths() {
    }

    public static boolean isPublic(String path) {
        return AS_SET.isPublic(path);
    }

    public static boolean isPublic(HttpServletRequest request) {
        return AS_SET.isPublic(request);
    }
}

package com.example.erp.notification.presentation.security;

import com.example.security.servlet.PublicPathSet;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Whitelist of paths that bypass authentication AND tenant-claim enforcement.
 * Both {@code SecurityConfig} and {@code TenantClaimEnforcer} reference this list
 * so the two stay in lockstep.
 *
 * <p>erp has NO public webhook / callback surface (E7 internal-only) — only the
 * actuator probes are unauthenticated (architecture.md § Security). The
 * prometheus scrape is network-isolated (internal docker network only).
 *
 * <p>The matching mechanism delegates to {@link PublicPathSet}
 * (ADR-MONO-058 § D5) — the {@code EXACT}/{@code PREFIXES} data below stays
 * this service's own, unmoved.
 */
public final class PublicPaths {

    public static final Set<String> EXACT = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus"
    );

    public static final Set<String> PREFIXES = Set.of(
            "/actuator/health/"
    );

    private static final PublicPathSet MECHANISM = PublicPathSet.of(EXACT, PREFIXES);

    private PublicPaths() {
    }

    public static boolean isPublic(String path) {
        return MECHANISM.isPublic(path);
    }

    public static boolean isPublic(HttpServletRequest request) {
        return MECHANISM.isPublic(request);
    }
}

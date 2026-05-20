package com.example.erp.masterdata.presentation.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Whitelist of paths that bypass authentication AND tenant-claim enforcement.
 * Both {@code SecurityConfig} and {@code TenantClaimEnforcer} reference this
 * list so the two stay in lockstep.
 *
 * <p>erp has NO public webhook/callback surface in v1 (E7 internal-only) —
 * only the actuator probes are unauthenticated (architecture.md § Security).
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

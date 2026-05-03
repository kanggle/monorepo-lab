package com.example.fanplatform.artist.adapter.in.web.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Centralized whitelist of paths that bypass authentication AND tenant-claim
 * enforcement.
 *
 * <p>Both {@code SecurityConfig} (which permits these paths in the Spring
 * Security filter chain) and {@code TenantClaimEnforcer} (which skips its
 * defense-in-depth tenant check on these paths) reference this list, so the
 * two stay in lockstep. Mirrors community-service.
 */
public final class PublicPaths {

    /** Exact-match public paths. */
    public static final Set<String> EXACT = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus"
    );

    /** Path prefixes whose subtree is public. Each entry MUST end with {@code /}. */
    public static final Set<String> PREFIXES = Set.of(
            "/actuator/health/"
    );

    private PublicPaths() {
    }

    /** Returns true if {@code path} matches any whitelisted path. */
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

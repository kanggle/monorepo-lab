package com.example.fanplatform.community.presentation.security;

import com.example.security.servlet.PublicPathSet;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Centralized whitelist of paths that bypass authentication AND tenant-claim
 * enforcement.
 *
 * <p>Both {@code SecurityConfig} (which permits these paths in the Spring
 * Security filter chain) and {@code TenantClaimEnforcer} (which skips its
 * defense-in-depth tenant check on these paths) reference this list, so the
 * two stay in lockstep. If a future actuator endpoint such as
 * {@code /actuator/env} or {@code /actuator/heapdump} is exposed, it will
 * not silently bypass the tenant gate — it must be added here explicitly.
 *
 * <p>The {@code /actuator/health/} prefix wildcard handles Kubernetes
 * liveness/readiness sub-paths ({@code /actuator/health/liveness},
 * {@code /actuator/health/readiness}).
 *
 * <p>The {@code EXACT}/{@code PREFIXES} matching mechanism delegates to
 * {@link PublicPathSet} (ADR-MONO-058 § D5) — this class supplies only the
 * data (community-service's own policy of what is public); the matching
 * logic itself is shared with the other three fan-platform services.
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

    private static final PublicPathSet MECHANISM = PublicPathSet.of(EXACT, PREFIXES);

    private PublicPaths() {
    }

    /** Returns true if {@code path} matches any whitelisted path. */
    public static boolean isPublic(String path) {
        return MECHANISM.isPublic(path);
    }

    /** Convenience overload for servlet filter usage. */
    public static boolean isPublic(HttpServletRequest request) {
        return MECHANISM.isPublic(request);
    }
}

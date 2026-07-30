package com.example.fanplatform.notification.presentation.security;

import com.example.security.servlet.PublicPathSet;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Centralized whitelist of paths that bypass authentication AND tenant-claim
 * enforcement on the inbox surface. Both the {@code SecurityFilterChain} and
 * {@code TenantClaimEnforcer} reference this list so the two stay in lockstep.
 *
 * <p>The {@code EXACT}/{@code PREFIXES} matching mechanism delegates to
 * {@link PublicPathSet} (ADR-MONO-058 § D5) — this class supplies only the
 * data (notification-service's own policy of what is public); the matching
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

    public static boolean isPublic(String path) {
        return MECHANISM.isPublic(path);
    }

    public static boolean isPublic(HttpServletRequest request) {
        return MECHANISM.isPublic(request);
    }
}

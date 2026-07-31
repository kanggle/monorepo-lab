package com.example.fanplatform.membership.presentation.security;

import com.example.security.servlet.PublicPathSet;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Centralized whitelist of paths that bypass authentication AND tenant-claim
 * enforcement on the end-user surface. Both the end-user {@code SecurityFilterChain}
 * and {@code TenantClaimEnforcer} reference this list so the two stay in lockstep.
 *
 * <p>The {@code EXACT}/{@code PREFIXES} matching mechanism delegates to
 * {@link PublicPathSet} (ADR-MONO-058 § D5) — this class supplies only the
 * data (membership-service's own policy of what is public, including the
 * PortOne webhook entry below); the matching logic itself is shared with the
 * other three fan-platform services.
 */
public final class PublicPaths {

    /** Exact-match public paths. */
    public static final Set<String> EXACT = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus",
            // PortOne V2 webhook (TASK-FAN-BE-033, ADR-002 §D3). Public by necessity:
            // PortOne cannot present a fan JWT and carries no tenant claim, so this
            // bypasses BOTH end-user auth AND tenant-claim enforcement. Its OWN auth is
            // the HMAC signature verified in PortOneWebhookController — an invalid
            // signature is rejected 401 there, not by Spring Security.
            "/webhooks/portone"
    );

    /** Path prefixes whose subtree is public. Each entry MUST end with {@code /}. */
    public static final Set<String> PREFIXES = Set.of(
            "/actuator/health/"
    );

    /**
     * The same data as a {@link PublicPathSet}, for callers that need the value object rather than a
     * predicate — the end-user {@code SecurityFilterChain} assembly (ADR-MONO-058 § D4) registers the
     * {@code permitAll()} matchers straight off it.
     *
     * <p>It is the <em>same instance</em> {@link #isPublic(String)} answers from, which is the point:
     * the paths Spring Security lets through unauthenticated and the paths {@code TenantClaimEnforcer}
     * skips its tenant check on cannot drift apart. That matters more here than in the sibling
     * services — this is the only fan {@code PublicPaths} carrying a business route
     * ({@code /webhooks/portone}) rather than actuator probes alone.
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

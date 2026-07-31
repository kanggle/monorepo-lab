package com.wms.admin.infra.security;

import com.example.security.servlet.PublicPathSet;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * admin-service's public (unauthenticated) request paths (ADR-MONO-058 § D5).
 *
 * <p>Lives alongside {@link OAuth2ResourceServerConfig} (this package) rather
 * than {@code com.wms.admin.config}, where {@code SecurityConfig} itself
 * sits — admin-service groups its security-infra classes (this one,
 * {@code OAuth2ResourceServerConfig}, {@code WmsTenantGatePolicyTest}) under
 * {@code infra.security} per {@code specs/services/admin-service/architecture.md}
 * § Architecture (Layered), so this class matches that existing convention
 * for consistency rather than sitting next to {@code SecurityConfig}.
 *
 * <p>The {@code EXACT}/{@code PREFIXES} matching mechanism delegates to
 * {@link PublicPathSet} (`libs/java-security-servlet`) — this class supplies
 * only the data (admin-service's own policy of what is public); the matching
 * logic itself is shared with every other project's {@code PublicPaths}
 * class.
 *
 * <p>The {@code /actuator/health/} prefix handles liveness/readiness
 * sub-paths ({@code /actuator/health/liveness}, {@code /actuator/health/readiness}).
 *
 * <p>{@link #asAntPatterns()} additionally emits the Ant-pattern-shaped array
 * {@code SecurityConfig} passes to Spring Security's
 * {@code .requestMatchers(...)} — {@link PublicPathSet} intentionally has no
 * such accessor itself (the shared type is mechanism-only; Spring-matcher
 * convenience is per-service policy, ADR-MONO-058 § D5).
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

    /** Convenience overload for servlet filter/security-config usage. */
    public static boolean isPublic(HttpServletRequest request) {
        return MECHANISM.isPublic(request);
    }

    /**
     * {@code EXACT} plus each {@code PREFIXES} entry suffixed with {@code **},
     * for {@code SecurityConfig}'s {@code .requestMatchers(...)} call — the
     * same classification the previous inline {@code PUBLIC_PATHS} Ant-pattern
     * array produced (ADR-MONO-058 § D5, AC-2).
     */
    public static String[] asAntPatterns() {
        List<String> patterns = new ArrayList<>(EXACT);
        for (String prefix : PREFIXES) {
            patterns.add(prefix + "**");
        }
        return patterns.toArray(new String[0]);
    }
}

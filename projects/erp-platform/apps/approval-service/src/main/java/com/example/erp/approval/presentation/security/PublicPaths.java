package com.example.erp.approval.presentation.security;

import com.example.security.servlet.PublicPathSet;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Whitelist of paths that bypass authentication AND tenant-claim enforcement.
 * Both {@code SecurityConfig} and {@code TenantClaimEnforcer} reference this
 * list so the two stay in lockstep.
 *
 * <p>erp has NO public webhook/callback surface in v1 (E7 internal-only) —
 * only the actuator probes are unauthenticated (architecture.md § Security).
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

    /**
     * The same {@code EXACT}/{@code PREFIXES} data as a {@link PublicPathSet} — the single
     * instance both the {@code isPublic} helpers below and this service's
     * {@code SecurityConfig} chain assembly (ADR-MONO-058 § D4) read, so the paths Spring
     * Security permits unauthenticated and the paths the tenant gate exempts cannot drift.
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

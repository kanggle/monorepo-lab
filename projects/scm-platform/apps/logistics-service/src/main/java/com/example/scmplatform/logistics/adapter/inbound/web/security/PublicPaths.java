package com.example.scmplatform.logistics.adapter.inbound.web.security;

import com.example.security.servlet.PublicPathSet;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * The paths that bypass authentication <strong>and</strong> tenant-claim enforcement.
 *
 * <p>{@code SecurityConfig} builds its {@code permitAll} matchers from this list and
 * {@code TenantClaimEnforcer} takes its exemption predicate from the same object (ADR-MONO-049
 * § 1.8) — one list, so the permit set and the tenant-gate exemption can no longer drift apart.
 * Only the three actuator probes are public; everything else under {@code /api/logistics/**}
 * requires an authenticated {@code tenant_id=scm} (or entitled) token.
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

    /** Empty by design — no prefixed public paths (mirrors demand-planning). */
    public static final Set<String> PREFIXES = Set.of();

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

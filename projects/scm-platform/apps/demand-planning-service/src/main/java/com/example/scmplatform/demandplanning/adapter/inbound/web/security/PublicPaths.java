package com.example.scmplatform.demandplanning.adapter.inbound.web.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * The paths that bypass authentication <strong>and</strong> tenant-claim enforcement.
 *
 * <p><strong>{@code SecurityConfig} builds its {@code permitAll} matchers from this list, and
 * {@code TenantClaimEnforcer} takes its exemption predicate from it.</strong> That is the whole
 * point of the class existing: before {@code TASK-MONO-385} the two were maintained in different
 * places — Spring Security permitted exactly these three paths while the tenant filter exempted
 * <em>all</em> of {@code /actuator/}. Nothing was reachable through the gap (see below), but
 * "nothing is reachable through the gap" was a fact about a *second* file, and the day someone
 * widened the permit list the tenant gate would have quietly widened with it. It cannot now:
 * there is one list.
 *
 * <p><strong>Deliberately no {@code /actuator/health/} prefix.</strong> procurement's
 * {@code PublicPaths} has one; this service's {@code SecurityConfig} has never permitted
 * {@code /actuator/health/liveness} and this class reproduces that exactly. Adding the prefix
 * here would widen the permit list — a behaviour change smuggled in under a refactor.
 * (ADR-MONO-049 § 1.8.)
 */
public final class PublicPaths {

    public static final Set<String> EXACT = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus"
    );

    /** Empty, and that is not an oversight — see the class Javadoc. */
    public static final Set<String> PREFIXES = Set.of();

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

package com.kanggle.platformconsole.bff.infrastructure.security;

import java.util.List;

/**
 * Public (unauthenticated) paths for the console-bff.
 *
 * <p>Only Actuator endpoints are public. All BFF composition routes are authenticated
 * (AC-7: {@code authenticated()} chain enforced for anything not in this list).
 *
 * <p>Architecture.md § Auth Flow inbound: {@code /actuator/health} is the Traefik
 * health-check probe target and must be {@code permitAll}.
 */
public final class PublicPaths {

    private PublicPaths() {}

    /**
     * Exact public paths (no wildcard).
     *
     * <p>{@code /actuator/prometheus} must be listed here as an exact match,
     * NOT only as a prefix. Spring Security 6.4's request matcher resolves
     * {@code AntPathRequestMatcher("/actuator/prometheus**")} differently
     * across servlet pipeline stages — under the OAuth2 Resource Server +
     * Servlet stack the prefix wildcard matched outbound scrapes
     * ({@code /actuator/prometheus/anything}) but missed the exact base
     * path operators actually scrape. CI surface: PR #669 4th run, IT body
     * {@code {"code":"UNAUTHORIZED","message":"Authentication required"}}.
     */
    public static final List<String> EXACT = List.of(
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus"
    );

    /**
     * Public path prefixes (callers append {@code **}).
     * Covers Actuator health probes (e.g. {@code /actuator/health/liveness},
     * {@code /actuator/health/readiness}) and any future Prometheus sub-paths.
     */
    public static final List<String> PREFIXES = List.of(
            "/actuator/health/",
            "/actuator/prometheus/"
    );
}

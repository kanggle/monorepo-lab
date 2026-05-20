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
     */
    public static final List<String> EXACT = List.of(
            "/actuator/health",
            "/actuator/info"
    );

    /**
     * Public path prefixes (callers append {@code **}).
     * Prometheus scrape endpoint is also public for the observability stack.
     */
    public static final List<String> PREFIXES = List.of(
            "/actuator/prometheus"
    );
}

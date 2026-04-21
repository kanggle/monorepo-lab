package com.example.gateway.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GatewayMetrics {

    private final Counter jwtMissing;
    private final Counter jwtExpired;
    private final Counter jwtInvalid;
    private final MeterRegistry registry;

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.jwtMissing = Counter.builder("gateway_jwt_validation_failure_total")
                .description("Total JWT validation failures by reason")
                .tag("reason", "missing")
                .register(registry);

        this.jwtExpired = Counter.builder("gateway_jwt_validation_failure_total")
                .description("Total JWT validation failures by reason")
                .tag("reason", "expired")
                .register(registry);

        this.jwtInvalid = Counter.builder("gateway_jwt_validation_failure_total")
                .description("Total JWT validation failures by reason")
                .tag("reason", "invalid")
                .register(registry);
    }

    public void incrementJwtValidationFailure(String reason) {
        switch (reason) {
            case "missing" -> jwtMissing.increment();
            case "expired" -> jwtExpired.increment();
            default -> jwtInvalid.increment();
        }
    }

    public void incrementRequestsRouted(String targetService) {
        registry.counter("gateway_requests_routed_total", "target", targetService)
                .increment();
    }

    public void incrementRateLimited(String route) {
        registry.counter("gateway_rate_limited_total", "route", route)
                .increment();
    }

    public void incrementUpstreamError(String targetService) {
        registry.counter("gateway_upstream_error_total", "target", targetService)
                .increment();
    }
}

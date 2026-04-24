package com.example.gateway.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayMetricsTest {

    private MeterRegistry registry;
    private GatewayMetrics gatewayMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        gatewayMetrics = new GatewayMetrics(registry);
    }

    @Test
    @DisplayName("JWT 검증 실패 시 reason별 gateway_jwt_validation_failure_total이 증가한다")
    void incrementJwtValidationFailure_incrementsCounterByReason() {
        gatewayMetrics.incrementJwtValidationFailure("missing");
        gatewayMetrics.incrementJwtValidationFailure("expired");
        gatewayMetrics.incrementJwtValidationFailure("invalid");
        gatewayMetrics.incrementJwtValidationFailure("missing");

        assertThat(registry.counter("gateway_jwt_validation_failure_total", "reason", "missing").count()).isEqualTo(2.0);
        assertThat(registry.counter("gateway_jwt_validation_failure_total", "reason", "expired").count()).isEqualTo(1.0);
        assertThat(registry.counter("gateway_jwt_validation_failure_total", "reason", "invalid").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("요청 라우팅 시 target별 gateway_requests_routed_total이 증가한다")
    void incrementRequestsRouted_incrementsCounterByTarget() {
        gatewayMetrics.incrementRequestsRouted("auth-service");
        gatewayMetrics.incrementRequestsRouted("product-service");
        gatewayMetrics.incrementRequestsRouted("auth-service");

        assertThat(registry.counter("gateway_requests_routed_total", "target", "auth-service").count()).isEqualTo(2.0);
        assertThat(registry.counter("gateway_requests_routed_total", "target", "product-service").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Rate limit 시 route별 gateway_rate_limited_total이 증가한다")
    void incrementRateLimited_incrementsCounterByRoute() {
        gatewayMetrics.incrementRateLimited("auth-service");

        assertThat(registry.counter("gateway_rate_limited_total", "route", "auth-service").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Upstream 에러 시 target별 gateway_upstream_error_total이 증가한다")
    void incrementUpstreamError_incrementsCounterByTarget() {
        gatewayMetrics.incrementUpstreamError("order-service");

        assertThat(registry.counter("gateway_upstream_error_total", "target", "order-service").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("requestsRouted를 1000번 호출해도 동일 태그에 대해 Counter가 중복 등록되지 않는다")
    void incrementRequestsRouted_repeated_noCounterLeak() {
        for (int i = 0; i < 1000; i++) {
            gatewayMetrics.incrementRequestsRouted("auth-service");
        }

        long meterCount = registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("gateway_requests_routed_total"))
                .count();
        assertThat(meterCount).isEqualTo(1);
        assertThat(registry.counter("gateway_requests_routed_total", "target", "auth-service").count()).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("rateLimited를 1000번 호출해도 Counter가 중복 등록되지 않는다")
    void incrementRateLimited_repeated_noCounterLeak() {
        for (int i = 0; i < 1000; i++) {
            gatewayMetrics.incrementRateLimited("auth-service");
        }

        long meterCount = registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("gateway_rate_limited_total"))
                .count();
        assertThat(meterCount).isEqualTo(1);
        assertThat(registry.counter("gateway_rate_limited_total", "route", "auth-service").count()).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("upstreamError를 1000번 호출해도 Counter가 중복 등록되지 않는다")
    void incrementUpstreamError_repeated_noCounterLeak() {
        for (int i = 0; i < 1000; i++) {
            gatewayMetrics.incrementUpstreamError("order-service");
        }

        long meterCount = registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("gateway_upstream_error_total"))
                .count();
        assertThat(meterCount).isEqualTo(1);
        assertThat(registry.counter("gateway_upstream_error_total", "target", "order-service").count()).isEqualTo(1000.0);
    }
}

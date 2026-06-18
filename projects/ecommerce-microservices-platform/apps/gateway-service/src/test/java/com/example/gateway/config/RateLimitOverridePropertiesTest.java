package com.example.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RateLimitOverrideProperties — per-tenant override lookup (TASK-BE-405 AC-2)")
class RateLimitOverridePropertiesTest {

    private static RateLimitOverrideProperties props() {
        RateLimitOverrideProperties p = new RateLimitOverrideProperties();
        p.setOverrides(Map.of(
                "acme", Map.of(
                        "product-service", new RateLimitOverrideProperties.Limit(500, 1000),
                        "order-service", new RateLimitOverrideProperties.Limit(50, 100))));
        return p;
    }

    @Test
    @DisplayName("resolve returns the override for a configured (tenant, route)")
    void resolve_present() {
        RateLimitOverrideProperties.Limit limit = props().resolve("acme", "product-service");
        assertThat(limit).isNotNull();
        assertThat(limit.getReplenishRate()).isEqualTo(500);
        assertThat(limit.getBurstCapacity()).isEqualTo(1000);
    }

    @Test
    @DisplayName("resolve returns null for a tenant without overrides")
    void resolve_unknownTenant() {
        assertThat(props().resolve("other", "product-service")).isNull();
    }

    @Test
    @DisplayName("resolve returns null for a route without an override on a known tenant")
    void resolve_unknownRoute() {
        assertThat(props().resolve("acme", "payment-service")).isNull();
    }

    @Test
    @DisplayName("resolve is null-safe on null tenant/route")
    void resolve_nullArgs() {
        RateLimitOverrideProperties p = props();
        assertThat(p.resolve(null, "product-service")).isNull();
        assertThat(p.resolve("acme", null)).isNull();
    }

    @Test
    @DisplayName("default (no overrides configured) resolves nothing")
    void resolve_emptyDefault() {
        assertThat(new RateLimitOverrideProperties().resolve("acme", "product-service")).isNull();
    }

    @Test
    @DisplayName("setOverrides(null) is coerced to an empty map (no NPE on resolve)")
    void setOverrides_nullCoerced() {
        RateLimitOverrideProperties p = new RateLimitOverrideProperties();
        p.setOverrides(null);
        assertThat(p.getOverrides()).isEmpty();
        assertThat(p.resolve("acme", "product-service")).isNull();
    }

    @Test
    @DisplayName("Limit.isValid: positive replenish and burst >= replenish")
    void limit_isValid() {
        assertThat(new RateLimitOverrideProperties.Limit(100, 200).isValid()).isTrue();
        assertThat(new RateLimitOverrideProperties.Limit(100, 100).isValid()).isTrue();
        assertThat(new RateLimitOverrideProperties.Limit(100, 50).isValid()).isFalse();
        assertThat(new RateLimitOverrideProperties.Limit(0, 100).isValid()).isFalse();
    }
}

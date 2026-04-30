package com.example.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway")
public class EdgeGatewayProperties {

    private JwtProperties jwt = new JwtProperties();
    private RateLimitProperties rateLimit = new RateLimitProperties();
    private TenantProperties tenant = new TenantProperties();
    private List<String> publicPaths = List.of();

    @Getter
    @Setter
    public static class JwtProperties {
        private String jwksUrl;
        private long jwksRefreshIntervalMs = 600_000;
        private long jwksCacheTtlSeconds = 600;
        private long gracePeriodSeconds = 300;
    }

    @Getter
    @Setter
    public static class RateLimitProperties {
        private boolean failOpen = true;
        private ScopeLimit login = new ScopeLimit(20, 60);
        private ScopeLimit signup = new ScopeLimit(5, 60);
        private ScopeLimit refresh = new ScopeLimit(10, 60);
        private ScopeLimit global = new ScopeLimit(100, 1);
    }

    @Getter
    @Setter
    public static class ScopeLimit {
        private int maxRequests;
        private long windowSeconds;

        public ScopeLimit() {
        }

        public ScopeLimit(int maxRequests, long windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
        }
    }

    @Getter
    @Setter
    public static class TenantProperties {
        private LegacyFallbackProperties legacyFallback = new LegacyFallbackProperties();

        @Getter
        @Setter
        public static class LegacyFallbackProperties {
            /** When true, JWTs missing tenant_id claim are treated as "fan-platform" (grace period). Default: false. */
            private boolean enabled = false;
            private String defaultTenantId = "fan-platform";
        }
    }
}

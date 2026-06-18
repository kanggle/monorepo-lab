package com.example.gateway.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-tenant rate-limit override configuration (TASK-BE-405 AC-2 — M7 realization,
 * ADR-MONO-030 Step 4 facet e).
 *
 * <p>The per-route {@code replenishRate}/{@code burstCapacity} defaults live in each
 * route's {@code RequestRateLimiter} filter args (application.yml) and are resolved
 * statically by Spring Cloud Gateway's {@link org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
 * RedisRateLimiter} per route id. That covers the common case: every tenant gets its own
 * bucket (the {@code (tenant, route)} key) of the route's default size.
 *
 * <p>This properties bean adds the <b>optional per-tenant override</b>: a tenant that needs
 * a larger (or smaller) bucket on a specific route can be granted one without touching the
 * route default. The shape is:
 *
 * <pre>{@code
 * ecommerce:
 *   ratelimit:
 *     overrides:
 *       acme:                 # tenantId (the tenant_id claim)
 *         product-service:    # routeId (the Spring Cloud Gateway route id)
 *           replenish-rate: 500
 *           burst-capacity: 1000
 *         order-service:
 *           replenish-rate: 50
 *           burst-capacity: 100
 * }</pre>
 *
 * <h2>Backward compatibility</h2>
 * With <b>no</b> {@code ecommerce.ratelimit.overrides} configured (the default), the map is
 * empty and resolution always falls through to the route/config default — behaviour is
 * net-zero identical to the config-default-only implementation.
 *
 * <h2>Decoupling</h2>
 * The override map is plain config; there is <b>no</b> coupling to
 * {@code tenant_domain_subscription} (the entitlement plane stays decoupled, TASK-BE-405
 * § Design decisions).
 */
@ConfigurationProperties(prefix = "ecommerce.ratelimit")
public class RateLimitOverrideProperties {

    /**
     * tenantId -&gt; (routeId -&gt; limit). An absent tenant or an absent route within a tenant
     * means "no override" — the caller falls back to the route/config default.
     */
    private Map<String, Map<String, Limit>> overrides = new LinkedHashMap<>();

    public Map<String, Map<String, Limit>> getOverrides() {
        return overrides;
    }

    public void setOverrides(Map<String, Map<String, Limit>> overrides) {
        this.overrides = (overrides == null) ? new LinkedHashMap<>() : overrides;
    }

    /**
     * Resolves the override limit for a {@code (tenantId, routeId)} tuple, or {@code null}
     * when none is configured (caller must fall back to the route default — never derive a
     * null limit from this).
     */
    public Limit resolve(String tenantId, String routeId) {
        if (tenantId == null || routeId == null) {
            return null;
        }
        Map<String, Limit> perRoute = overrides.get(tenantId);
        if (perRoute == null) {
            return null;
        }
        return perRoute.get(routeId);
    }

    /** A single override bucket size. {@code replenishRate} tokens/sec, {@code burstCapacity} bucket depth. */
    public static class Limit {

        private int replenishRate;
        private int burstCapacity;

        public Limit() {
        }

        public Limit(int replenishRate, int burstCapacity) {
            this.replenishRate = replenishRate;
            this.burstCapacity = burstCapacity;
        }

        public int getReplenishRate() {
            return replenishRate;
        }

        public void setReplenishRate(int replenishRate) {
            this.replenishRate = replenishRate;
        }

        public int getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(int burstCapacity) {
            this.burstCapacity = burstCapacity;
        }

        /** Valid only when both fields are positive and burst &gt;= replenish (SCG's {@code Config} invariant). */
        public boolean isValid() {
            return replenishRate >= 1 && burstCapacity >= replenishRate;
        }

        @Override
        public String toString() {
            return "Limit{replenishRate=" + replenishRate + ", burstCapacity=" + burstCapacity + '}';
        }
    }
}

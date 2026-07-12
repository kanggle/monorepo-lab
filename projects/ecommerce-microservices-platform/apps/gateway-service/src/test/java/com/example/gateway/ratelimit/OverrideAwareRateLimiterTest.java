package com.example.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gateway.config.RateLimitOverrideProperties;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("OverrideAwareRateLimiter — per-tenant rate-limit override resolution (TASK-BE-405 AC-2)")
class OverrideAwareRateLimiterTest {

    private static final String ROUTE = "product-service";
    private static final String DEFAULT_TENANT = "ecommerce";
    private static final String OVERRIDE_TENANT = "acme";

    private static final String SUBJECT = "shopper-1";

    /**
     * {@code tenantRouteKeyResolver} authenticated key shape as it is actually emitted
     * (TASK-MONO-368) — tenant <em>and</em> account.
     */
    private static String authKey(String routeId, String tenantId, String subject) {
        return "rate:ecommerce-gw:" + routeId + ":t:" + tenantId + ":acct:" + subject;
    }

    /**
     * The production authenticated key for an arbitrary account. Every override test below goes
     * through this, so they all exercise the account-qualified shape the resolver really emits —
     * a parser that stopped extracting the tenant at {@code :acct:} would fail them, not just the
     * one test that names the segment.
     */
    private static String authKey(String routeId, String tenantId) {
        return authKey(routeId, tenantId, SUBJECT);
    }

    /** Degraded authenticated key: a token with no usable {@code sub} (tenant-only bucket). */
    private static String authKeyNoSubject(String routeId, String tenantId) {
        return "rate:ecommerce-gw:" + routeId + ":t:" + tenantId;
    }

    /** {@code tenantRouteKeyResolver} anonymous/pre-auth key shape (default tenant + IP). */
    private static String anonKey(String routeId, String ip) {
        return "rate:ecommerce-gw:" + routeId + ":t:" + DEFAULT_TENANT + ":ip:" + ip;
    }

    private static RateLimitOverrideProperties propsWith(String tenant, String route,
                                                         int replenish, int burst) {
        RateLimitOverrideProperties props = new RateLimitOverrideProperties();
        props.setOverrides(Map.of(tenant,
                Map.of(route, new RateLimitOverrideProperties.Limit(replenish, burst))));
        return props;
    }

    private static RedisRateLimiter.Response allowed() {
        return new RedisRateLimiter.Response(true, Map.of());
    }

    // ---- tenant parsing -----------------------------------------------------

    /**
     * TASK-MONO-368 § AC-2 — the parser must stop at {@code :acct:}. If it stopped only at
     * {@code :ip:}, this would return {@code "acme:acct:shopper-1"}: a different string for every
     * account, so {@code overrides.acme.<route>} would never match again and every per-tenant
     * override would silently stop applying — no error, no log, and every other test in this
     * class still green.
     */
    @Test
    @DisplayName("parseTenant stops at the account segment (else per-tenant overrides silently die)")
    void parseTenant_authenticatedKeyWithAccount() {
        assertThat(OverrideAwareRateLimiter.parseTenant(authKey(ROUTE, OVERRIDE_TENANT, "shopper-1")))
                .isEqualTo(OVERRIDE_TENANT);
    }

    @Test
    @DisplayName("parseTenant extracts the tenant from a degraded (no-sub) authenticated key")
    void parseTenant_authenticatedKeyWithoutAccount() {
        assertThat(OverrideAwareRateLimiter.parseTenant(authKeyNoSubject(ROUTE, OVERRIDE_TENANT)))
                .isEqualTo(OVERRIDE_TENANT);
    }

    @Test
    @DisplayName("parseTenant extracts the default tenant from an anonymous (IP-qualified) key")
    void parseTenant_anonymousKey() {
        assertThat(OverrideAwareRateLimiter.parseTenant(anonKey(ROUTE, "10.0.0.1")))
                .isEqualTo(DEFAULT_TENANT);
    }

    /**
     * The end-to-end half of AC-2. A unit assertion on {@code parseTenant} alone would not catch a
     * regression that reintroduced the {@code :ip:}-only terminator <em>and</em> updated the parse
     * test to match: this asserts the override actually still <b>applies</b> to the key shape the
     * resolver really emits.
     */
    @Test
    @DisplayName("override still applies to a real account-qualified authenticated key")
    void overridePresent_appliesToAccountQualifiedKey() {
        RedisRateLimiter defaultDelegate = mock(RedisRateLimiter.class);
        RedisRateLimiter overrideLimiter = mock(RedisRateLimiter.class);
        when(overrideLimiter.getConfig()).thenReturn(new java.util.HashMap<>());
        when(overrideLimiter.isAllowed(eq(ROUTE), anyString())).thenReturn(Mono.just(allowed()));

        Function<RedisRateLimiter.Config, RedisRateLimiter> factory = config -> overrideLimiter;
        OverrideAwareRateLimiter limiter = new OverrideAwareRateLimiter(
                defaultDelegate, propsWith(OVERRIDE_TENANT, ROUTE, 500, 1000), factory);

        String key = authKey(ROUTE, OVERRIDE_TENANT, "shopper-1");
        StepVerifier.create(limiter.isAllowed(ROUTE, key))
                .expectNextMatches(RateLimiter.Response::isAllowed)
                .verifyComplete();

        verify(overrideLimiter).isAllowed(ROUTE, key);
        verify(defaultDelegate, never()).isAllowed(anyString(), anyString());
    }

    /**
     * Two accounts of the same overridden tenant share the tenant's override <em>limits</em> but
     * are metered on <b>separate buckets</b> (distinct keys) — the property MONO-368 restored.
     */
    @Test
    @DisplayName("같은 tenant 의 두 계정 → 같은 override 적용, 서로 다른 버킷")
    void overridePresent_perAccountBucketsUnderOneTenantOverride() {
        RedisRateLimiter defaultDelegate = mock(RedisRateLimiter.class);
        RedisRateLimiter overrideLimiter = mock(RedisRateLimiter.class);
        when(overrideLimiter.getConfig()).thenReturn(new java.util.HashMap<>());
        when(overrideLimiter.isAllowed(eq(ROUTE), anyString())).thenReturn(Mono.just(allowed()));

        Function<RedisRateLimiter.Config, RedisRateLimiter> factory = config -> overrideLimiter;
        OverrideAwareRateLimiter limiter = new OverrideAwareRateLimiter(
                defaultDelegate, propsWith(OVERRIDE_TENANT, ROUTE, 500, 1000), factory);

        String keyA = authKey(ROUTE, OVERRIDE_TENANT, "shopper-a");
        String keyB = authKey(ROUTE, OVERRIDE_TENANT, "shopper-b");
        StepVerifier.create(limiter.isAllowed(ROUTE, keyA)).expectNextCount(1).verifyComplete();
        StepVerifier.create(limiter.isAllowed(ROUTE, keyB)).expectNextCount(1).verifyComplete();

        verify(overrideLimiter).isAllowed(ROUTE, keyA);
        verify(overrideLimiter).isAllowed(ROUTE, keyB);
        verify(defaultDelegate, never()).isAllowed(anyString(), anyString());
    }

    @Test
    @DisplayName("parseTenant returns null for a key without a tenant segment")
    void parseTenant_noTenantSegment() {
        assertThat(OverrideAwareRateLimiter.parseTenant("192.168.1.1")).isNull();
        assertThat(OverrideAwareRateLimiter.parseTenant(null)).isNull();
    }

    // ---- override present ---------------------------------------------------

    @Test
    @DisplayName("override present for (tenant, route) → uses the override limiter, not the default")
    void overridePresent_usesOverrideLimiter() {
        RedisRateLimiter defaultDelegate = mock(RedisRateLimiter.class);
        RedisRateLimiter overrideLimiter = mock(RedisRateLimiter.class);
        when(overrideLimiter.getConfig()).thenReturn(new java.util.HashMap<>());
        when(overrideLimiter.isAllowed(eq(ROUTE), anyString())).thenReturn(Mono.just(allowed()));

        Function<RedisRateLimiter.Config, RedisRateLimiter> factory = config -> overrideLimiter;
        OverrideAwareRateLimiter limiter = new OverrideAwareRateLimiter(
                defaultDelegate, propsWith(OVERRIDE_TENANT, ROUTE, 500, 1000), factory);

        String key = authKey(ROUTE, OVERRIDE_TENANT);
        StepVerifier.create(limiter.isAllowed(ROUTE, key))
                .expectNextMatches(RateLimiter.Response::isAllowed)
                .verifyComplete();

        verify(overrideLimiter).isAllowed(ROUTE, key);
        verify(defaultDelegate, never()).isAllowed(anyString(), anyString());
    }

    @Test
    @DisplayName("override limiter is built with the override limits and seeds them per route")
    void overridePresent_seedsOverrideConfig() {
        RedisRateLimiter defaultDelegate = mock(RedisRateLimiter.class);
        RedisRateLimiter overrideLimiter = mock(RedisRateLimiter.class);
        Map<String, RedisRateLimiter.Config> seeded = new java.util.HashMap<>();
        when(overrideLimiter.getConfig()).thenReturn(seeded);
        when(overrideLimiter.isAllowed(anyString(), anyString())).thenReturn(Mono.just(allowed()));

        Function<RedisRateLimiter.Config, RedisRateLimiter> factory = config -> overrideLimiter;
        OverrideAwareRateLimiter limiter = new OverrideAwareRateLimiter(
                defaultDelegate, propsWith(OVERRIDE_TENANT, ROUTE, 500, 1000), factory);

        limiter.isAllowed(ROUTE, authKey(ROUTE, OVERRIDE_TENANT)).block();

        assertThat(seeded).containsKey(ROUTE);
        assertThat(seeded.get(ROUTE).getReplenishRate()).isEqualTo(500);
        assertThat(seeded.get(ROUTE).getBurstCapacity()).isEqualTo(1000);
    }

    @Test
    @DisplayName("override limiter is built once per (tenant, route) and cached across calls")
    void overridePresent_cachesPerTuple() {
        RedisRateLimiter defaultDelegate = mock(RedisRateLimiter.class);
        RedisRateLimiter overrideLimiter = mock(RedisRateLimiter.class);
        when(overrideLimiter.getConfig()).thenReturn(new java.util.HashMap<>());
        when(overrideLimiter.isAllowed(anyString(), anyString())).thenReturn(Mono.just(allowed()));

        int[] builds = {0};
        Function<RedisRateLimiter.Config, RedisRateLimiter> factory = config -> {
            builds[0]++;
            return overrideLimiter;
        };
        OverrideAwareRateLimiter limiter = new OverrideAwareRateLimiter(
                defaultDelegate, propsWith(OVERRIDE_TENANT, ROUTE, 500, 1000), factory);

        String key = authKey(ROUTE, OVERRIDE_TENANT);
        limiter.isAllowed(ROUTE, key).block();
        limiter.isAllowed(ROUTE, key).block();
        limiter.isAllowed(ROUTE, key).block();

        assertThat(builds[0]).isEqualTo(1);
        assertThat(limiter.cachedOverrideKeys()).containsExactly(ROUTE + "|" + OVERRIDE_TENANT);
        verify(overrideLimiter, times(3)).isAllowed(ROUTE, key);
    }

    // ---- override absent → default path ------------------------------------

    @Test
    @DisplayName("override absent for the tenant → falls back to the route/config default limiter")
    void overrideAbsent_usesDefault() {
        RedisRateLimiter defaultDelegate = mock(RedisRateLimiter.class);
        when(defaultDelegate.isAllowed(anyString(), anyString())).thenReturn(Mono.just(allowed()));
        Function<RedisRateLimiter.Config, RedisRateLimiter> factory = config -> {
            throw new AssertionError("override limiter must not be built when no override exists");
        };
        OverrideAwareRateLimiter limiter = new OverrideAwareRateLimiter(
                defaultDelegate, propsWith(OVERRIDE_TENANT, ROUTE, 500, 1000), factory);

        // a DIFFERENT tenant on the same route has no override
        String key = authKey(ROUTE, "other-tenant");
        StepVerifier.create(limiter.isAllowed(ROUTE, key))
                .expectNextMatches(RateLimiter.Response::isAllowed)
                .verifyComplete();

        verify(defaultDelegate).isAllowed(ROUTE, key);
    }

    @Test
    @DisplayName("default-tenant / no-claim anonymous key with no override → default limiter")
    void defaultTenantAnonymous_usesDefault() {
        RedisRateLimiter defaultDelegate = mock(RedisRateLimiter.class);
        when(defaultDelegate.isAllowed(anyString(), anyString())).thenReturn(Mono.just(allowed()));
        OverrideAwareRateLimiter limiter = new OverrideAwareRateLimiter(
                defaultDelegate, new RateLimitOverrideProperties(), config -> {
            throw new AssertionError("no override configured");
        });

        String key = anonKey(ROUTE, "10.0.0.9");
        limiter.isAllowed(ROUTE, key).block();

        verify(defaultDelegate).isAllowed(ROUTE, key);
        assertThat(limiter.cachedOverrideKeys()).isEmpty();
    }

    @Test
    @DisplayName("no overrides configured at all → behaviour is net-zero (always default path)")
    void noOverridesConfigured_netZero() {
        RedisRateLimiter defaultDelegate = mock(RedisRateLimiter.class);
        when(defaultDelegate.isAllowed(anyString(), anyString())).thenReturn(Mono.just(allowed()));
        OverrideAwareRateLimiter limiter = new OverrideAwareRateLimiter(
                defaultDelegate, new RateLimitOverrideProperties(), config -> {
            throw new AssertionError("no override configured");
        });

        limiter.isAllowed(ROUTE, authKey(ROUTE, "acme")).block();
        limiter.isAllowed("order-service", authKey("order-service", DEFAULT_TENANT)).block();

        verify(defaultDelegate, times(2)).isAllowed(anyString(), anyString());
    }

    @Test
    @DisplayName("override present for a DIFFERENT route on the same tenant → default for the other route")
    void overrideRouteScoped_usesDefaultForOtherRoute() {
        RedisRateLimiter defaultDelegate = mock(RedisRateLimiter.class);
        when(defaultDelegate.isAllowed(anyString(), anyString())).thenReturn(Mono.just(allowed()));
        OverrideAwareRateLimiter limiter = new OverrideAwareRateLimiter(
                defaultDelegate, propsWith(OVERRIDE_TENANT, "order-service", 50, 100), config -> {
            throw new AssertionError("override is for order-service, not product-service");
        });

        String key = authKey(ROUTE, OVERRIDE_TENANT);
        limiter.isAllowed(ROUTE, key).block();

        verify(defaultDelegate).isAllowed(ROUTE, key);
    }

    @Test
    @DisplayName("invalid override (burst < replenish) → ignored, falls back to default (never null limits)")
    void invalidOverride_fallsBackToDefault() {
        RedisRateLimiter defaultDelegate = mock(RedisRateLimiter.class);
        when(defaultDelegate.isAllowed(anyString(), anyString())).thenReturn(Mono.just(allowed()));
        // burst (10) < replenish (100) → invalid per SCG Config invariant
        OverrideAwareRateLimiter limiter = new OverrideAwareRateLimiter(
                defaultDelegate, propsWith(OVERRIDE_TENANT, ROUTE, 100, 10), config -> {
            throw new AssertionError("invalid override must not build an override limiter");
        });

        String key = authKey(ROUTE, OVERRIDE_TENANT);
        limiter.isAllowed(ROUTE, key).block();

        verify(defaultDelegate).isAllowed(ROUTE, key);
    }

    // ---- delegation passthrough --------------------------------------------

    @Test
    @DisplayName("config accessors delegate to the default limiter")
    void configAccessors_delegate() {
        RedisRateLimiter defaultDelegate = mock(RedisRateLimiter.class);
        Map<String, RedisRateLimiter.Config> cfg = new java.util.HashMap<>();
        when(defaultDelegate.getConfig()).thenReturn(cfg);
        when(defaultDelegate.getConfigClass()).thenReturn(RedisRateLimiter.Config.class);
        RedisRateLimiter.Config fresh = new RedisRateLimiter.Config();
        when(defaultDelegate.newConfig()).thenReturn(fresh);

        OverrideAwareRateLimiter limiter = new OverrideAwareRateLimiter(
                defaultDelegate, new RateLimitOverrideProperties(), config -> mock(RedisRateLimiter.class));

        assertThat(limiter.getConfig()).isSameAs(cfg);
        assertThat(limiter.getConfigClass()).isEqualTo(RedisRateLimiter.Config.class);
        assertThat(limiter.newConfig()).isSameAs(fresh);
    }
}

package com.example.gateway.config;

import com.example.gateway.ratelimit.FailOpenRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Per-tenant API rate-limit wiring (TASK-BE-405 — M7 realization, ADR-MONO-030 Step 4
 * facet e). Realizes {@code rules/traits/multi-tenant.md} M7: the gateway rate limit is
 * keyed by the {@code (tenant_id, route_id)} tuple so one tenant's burst cannot consume
 * another tenant's bucket / affect its latency.
 *
 * <h2>Key shape</h2>
 * <ul>
 *   <li><b>Authenticated (post-auth)</b> — {@code rate:ecommerce-gw:<routeId>:t:<tenantId>}.
 *       The tenant is the {@code tenant_id} claim of the JWKS- and issuer-verified JWT,
 *       read from the reactive security context (the authoritative source — the gate has
 *       already rejected a blank/missing claim, and this does not depend on the
 *       {@code X-Tenant-Id} header injected by {@code JwtHeaderEnrichmentFilter} whose
 *       ordering relative to the {@code RequestRateLimiter} filter is unspecified).</li>
 *   <li><b>Anonymous / pre-auth</b> ({@code /api/search/**}, public {@code GET
 *       /api/products/**}, the carrier webhook) — no security context, so the tenant
 *       resolves to the <b>default tenant</b> {@value #DEFAULT_TENANT} (D8 net-zero —
 *       a standalone single-store behaves exactly as today) and the key is further
 *       qualified by the client IP: {@code rate:ecommerce-gw:<routeId>:t:ecommerce:ip:<ip>}.
 *       This preserves the IP-based DoS / brute-force bounding the legacy
 *       {@code ipKeyResolver} provided on pre-auth routes (TASK-BE-405 § Failure
 *       Scenarios — "IP-limit removed without replacement"); without the IP suffix every
 *       anonymous caller on a public route would collapse into one shared default-tenant
 *       bucket.</li>
 * </ul>
 *
 * <h2>Degrade</h2>
 * The tenant key is <b>never null</b> (default-tenant fallback, TASK-BE-405 § Failure
 * Scenarios). Redis unavailability <b>fails open</b> via {@link FailOpenRateLimiter} —
 * rate limiting is additive, never a hard dependency.
 *
 * <h2>Limits</h2>
 * Per-route default {@code replenishRate}/{@code burstCapacity} live in the route's
 * {@code RequestRateLimiter} filter args (application.yml). Spring Cloud Gateway resolves
 * the limiter config per route id, so the bucket size is a route default; the
 * {@code (tenant, route)} key gives each tenant its own bucket of that size. Per-tenant
 * overrides are an additive future increment (the entitlement plane stays decoupled —
 * no coupling to {@code tenant_domain_subscription}).
 */
@Configuration
public class TenantRouteRateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(TenantRouteRateLimitConfig.class);

    /** Shared-Redis key prefix — avoids cross-project collisions (M1 Redis key prefix). */
    static final String KEY_PREFIX = "rate:ecommerce-gw";
    /** D8 net-zero default tenant — a token-less request resolves here. */
    static final String DEFAULT_TENANT = "ecommerce";
    static final String CLAIM_TENANT_ID = "tenant_id";
    static final String UNKNOWN_ROUTE = "unknown";
    static final String UNKNOWN_IP = "unknown";

    /**
     * Tenant-aware {@link KeyResolver} keyed by the {@code (tenant_id, route_id)} tuple
     * (M7). For authenticated requests the tenant is the JWT {@code tenant_id} claim; for
     * anonymous/pre-auth requests it falls back to the default tenant qualified by client
     * IP (so pre-auth routes stay IP-bounded). Marked {@code @Primary} so it is the
     * {@code KeyResolver} injected when a route's {@code key-resolver} SpEL is omitted; the
     * routes reference it explicitly by name ({@code #{@tenantRouteKeyResolver}}).
     */
    @Bean
    @Primary
    public KeyResolver tenantRouteKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(token -> authenticatedKey(exchange, token.getToken()))
                .switchIfEmpty(Mono.fromSupplier(() -> anonymousKey(exchange)));
    }

    /** {@code rate:ecommerce-gw:<routeId>:t:<tenantId>} — never a null tenant. */
    private static String authenticatedKey(ServerWebExchange exchange, Jwt jwt) {
        String tenantId = resolveTenant(jwt);
        return KEY_PREFIX + ":" + resolveRouteId(exchange) + ":t:" + tenantId;
    }

    /**
     * {@code rate:ecommerce-gw:<routeId>:t:ecommerce:ip:<ip>} — default tenant + client IP
     * so anonymous traffic on a public route stays IP-bounded (no single shared bucket).
     */
    private static String anonymousKey(ServerWebExchange exchange) {
        return KEY_PREFIX + ":" + resolveRouteId(exchange)
                + ":t:" + DEFAULT_TENANT + ":ip:" + resolveClientIp(exchange);
    }

    /** Never returns null/blank — a blank claim resolves to the default tenant (D8). */
    private static String resolveTenant(Jwt jwt) {
        String tenantId = jwt.getClaimAsString(CLAIM_TENANT_ID);
        return (tenantId == null || tenantId.isBlank()) ? DEFAULT_TENANT : tenantId;
    }

    private static String resolveRouteId(ServerWebExchange exchange) {
        Object attr = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (attr instanceof Route route) {
            return route.getId();
        }
        log.warn("Rate-limit key resolver invoked without a GATEWAY_ROUTE_ATTR; routeId='{}'", UNKNOWN_ROUTE);
        return UNKNOWN_ROUTE;
    }

    private static String resolveClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma < 0 ? forwarded.trim() : forwarded.substring(0, comma).trim();
        }
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(addr -> addr.getHostAddress())
                .orElse(UNKNOWN_IP);
    }

    /**
     * Primary {@link RateLimiter} exposed to Spring Cloud Gateway. Wraps the autoconfigured
     * {@link RedisRateLimiter} with fail-open semantics (TASK-BE-405 § Degrade). On Redis
     * connectivity errors requests are allowed through with a WARN log + metric, rather than
     * 5xx-ing the edge.
     */
    @Bean
    @Primary
    public RateLimiter<RedisRateLimiter.Config> failOpenRateLimiter(RedisRateLimiter delegate,
                                                                    MeterRegistry meterRegistry) {
        return new FailOpenRateLimiter(delegate, meterRegistry);
    }
}

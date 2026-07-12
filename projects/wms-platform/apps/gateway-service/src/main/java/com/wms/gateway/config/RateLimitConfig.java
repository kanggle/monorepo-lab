package com.wms.gateway.config;

import com.example.apigateway.ratelimit.FailOpenRateLimiter;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rate-limit keying for wms's edge.
 *
 * <h2>Why the key follows the account (TASK-MONO-370)</h2>
 *
 * Every wms route is JWT-authenticated. Keying such traffic by client IP throws away an
 * identity we already have: everyone behind one warehouse NAT shares a bucket, while an abuser
 * rotating IPs is never throttled per account. {@code platform/api-gateway-policy.md}
 * § Rate Limiting > Key shape therefore requires the authenticated principal.
 *
 * <p><b>This was not a bug — it was compliance with the old policy.</b> Before TASK-MONO-368
 * that file declared {@code (clientIp, routeId)} as the platform default, and wms was the one
 * gateway that conformed to it. 368 raised the rule and deliberately left wms as a
 * <em>recorded deviation</em> rather than silently changing who gets 429'd on a live edge. This
 * task resolves it.
 *
 * <p>The IP key remains as the fallback for any request that arrives without a security
 * context, so a pre-auth path degrades to exactly the previous behaviour instead of producing a
 * null key or collapsing every such caller into one synthetic bucket.
 */
@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    static final String UNKNOWN_IP = "unknown";
    static final String UNKNOWN_ROUTE = "unknown";
    /** wms key prefix — an unprefixed key collides across domains the moment two share a Redis. */
    static final String KEY_PREFIX = "rate:wms-platform";

    /**
     * Key resolver for unauthenticated requests: {@code rate:wms-platform:<routeId>:<clientIp>}.
     * <p>
     * Client IP resolution order: {@code X-Forwarded-For} first value → remote address →
     * {@code unknown}. Route id is pulled from
     * {@link ServerWebExchangeUtils#GATEWAY_ROUTE_ATTR}; when missing (e.g. pre-routing),
     * {@code unknown} is used and a WARN is logged — never throw NPE on resolution.
     */
    @Bean("clientIpKeyResolver")
    KeyResolver clientIpKeyResolver() {
        return exchange -> Mono.just(buildKey(resolveRouteId(exchange), resolveClientIp(exchange)));
    }

    /**
     * Key resolver for authenticated requests — keyed on the JWT subject (account id for human
     * operators, client_id for {@code client_credentials} tokens, which is correct: a service
     * account gets its own bucket). Falls back to the IP key when no security context is present.
     */
    @Bean("accountKeyResolver")
    @Primary
    KeyResolver accountKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                // flatMap + justOrEmpty, not map: Reactor throws NPE if a map lambda returns
                // null, so a token with no `sub` would blow up before any filter could see it.
                // Empty here means "no usable identity" and falls through to the IP key below.
                .flatMap(token -> Mono.justOrEmpty(token.getToken().getSubject()))
                .filter(subject -> !subject.isBlank())
                .map(subject -> buildKey(resolveRouteId(exchange), "acct:" + subject))
                .switchIfEmpty(Mono.fromSupplier(
                        () -> buildKey(resolveRouteId(exchange), resolveClientIp(exchange))));
    }

    /**
     * Primary {@link RateLimiter} exposed to Spring Cloud Gateway. Wraps the
     * autoconfigured {@link RedisRateLimiter} with fail-open semantics: on <em>Redis</em>
     * connectivity errors, requests are allowed through with a WARN log. Rate limiting
     * is a soft protection, not a correctness boundary — see {@code api-gateway-policy.md}.
     * <p>
     * Fail-open is scoped to Redis-class failures only (TASK-BE-502); any other error
     * propagates so it surfaces as a 5xx rather than silently disabling the limiter.
     */
    @Bean
    @Primary
    RateLimiter<RedisRateLimiter.Config> failOpenRateLimiter(
            RedisRateLimiter delegate, MeterRegistry meterRegistry) {
        return new FailOpenRateLimiter(delegate, meterRegistry);
    }

    private static String buildKey(String routeId, String identity) {
        return KEY_PREFIX + ":" + routeId + ":" + identity;
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

    private static String resolveRouteId(ServerWebExchange exchange) {
        Object attr = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (attr instanceof Route route) {
            return route.getId();
        }
        log.warn("Rate-limit key resolver invoked without a GATEWAY_ROUTE_ATTR; falling back to routeId='{}'",
                UNKNOWN_ROUTE);
        return UNKNOWN_ROUTE;
    }
}

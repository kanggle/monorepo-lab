package com.example.finance.gateway.config;

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
 * Rate-limit keying for finance's edge.
 *
 * <h2>Why this shape and not wms's</h2>
 *
 * The fleet has two rate-limit keying shapes, and only one of them can be justified:
 *
 * <ul>
 *   <li><strong>scm / fan</strong> — key by the authenticated account's {@code sub}, falling back
 *       to client IP for pre-auth traffic, under a project-scoped key prefix.</li>
 *   <li><strong>wms</strong> — key by client IP only, with <em>no</em> prefix (its Redis keys are
 *       a bare {@code {ip}:{routeId}}), and <strong>no documented rationale</strong> for either
 *       choice.</li>
 * </ul>
 *
 * TASK-MONO-355 measured that difference and descoped it: choosing a default on an axis with no
 * owner would have been an ownerless policy decision, so it remains open for a human. A
 * <em>new</em> gateway, though, has to pick something — and it must not pick the unjustified
 * shape, because copying an unresolved decision into new code propagates it instead of resolving
 * it.
 *
 * <p>So finance takes the shape that can be defended. Keying by IP alone means everyone behind
 * one NAT shares a bucket while an authenticated abuser rotating IPs is unthrottled per account;
 * an unprefixed key collides across domains the moment two projects share a Redis. Neither is a
 * property to inherit on purpose.
 */
@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    static final String UNKNOWN_IP = "unknown";
    static final String UNKNOWN_ROUTE = "unknown";
    static final String KEY_PREFIX = "rate:finance-platform";

    /** Pre-auth / public traffic: {@code rate:finance-platform:<routeId>:<clientIp>}. */
    @Bean("clientIpKeyResolver")
    KeyResolver clientIpKeyResolver() {
        return exchange -> Mono.just(buildKey(resolveRouteId(exchange), resolveClientIp(exchange)));
    }

    /**
     * Authenticated traffic: {@code rate:finance-platform:<routeId>:acct:<sub>}, falling back to
     * the client IP when no JWT is present. The bucket follows the account, not the network path.
     */
    @Bean("accountKeyResolver")
    @Primary
    KeyResolver accountKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(token -> buildKey(resolveRouteId(exchange),
                        "acct:" + token.getToken().getSubject()))
                .switchIfEmpty(Mono.just(buildKey(resolveRouteId(exchange), resolveClientIp(exchange))));
    }

    /**
     * Wraps the autoconfigured {@link RedisRateLimiter} with fail-open semantics: on
     * <em>Redis</em> connectivity errors requests are allowed through with a WARN. Rate limiting
     * is a soft protection, not a correctness boundary.
     *
     * <p>Fail-open is scoped to Redis-class failures only (TASK-BE-502) — any other error
     * propagates, so a programming bug surfaces as a 5xx instead of silently disabling the
     * limiter. The original implementation swallowed every {@code Throwable}; that fix reached
     * three gateways and missed the fourth, which is the incident behind ADR-MONO-048. Do not
     * widen it back.
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

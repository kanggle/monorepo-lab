package com.example.erp.gateway.config;

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
 * Rate-limit keying for erp's edge.
 *
 * <h2>Why key on the account</h2>
 *
 * Anonymous traffic has no identity, so it can only be bucketed by client IP — that is what
 * {@code platform/api-gateway-policy.md} § Rate Limiting prescribes. Authenticated traffic
 * <em>does</em> have an identity, and bucketing it by IP throws that away: everyone behind one
 * NAT shares a bucket, while an abuser rotating IPs is never throttled per account. So erp keys
 * pre-auth requests by IP and authenticated requests by the JWT {@code sub}, both under a
 * project-scoped prefix that cannot collide with another domain sharing a Redis.
 *
 * <p><b>Correction (TASK-MONO-368 → 370).</b> This Javadoc once asserted that wms — which keyed by
 * client IP only — had "no documented rationale" and had picked an unjustifiable shape. That was
 * false: {@code api-gateway-policy.md} L92 declared {@code (clientIp, routeId)} as the platform
 * <em>default</em> at the time, so wms was the gateway that <b>conformed</b>. The claim was
 * asserted from a fleet head-count instead of read from the policy. MONO-368 raised the rule and
 * left wms as a recorded deviation rather than changing a live edge silently; MONO-370 made that
 * decision and aligned it. The fleet now keys authenticated traffic on the principal everywhere —
 * but the lesson is the mistake, not the outcome: a source comment does not get to settle a
 * platform question by assertion.
 */
@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    static final String UNKNOWN_IP = "unknown";
    static final String UNKNOWN_ROUTE = "unknown";
    static final String KEY_PREFIX = "rate:erp-platform";

    /** Pre-auth / public traffic: {@code rate:erp-platform:<routeId>:<clientIp>}. */
    @Bean("clientIpKeyResolver")
    KeyResolver clientIpKeyResolver() {
        return exchange -> Mono.just(buildKey(resolveRouteId(exchange), resolveClientIp(exchange)));
    }

    /**
     * Authenticated traffic: {@code rate:erp-platform:<routeId>:acct:<sub>}, falling back to
     * the client IP when no JWT is present. The bucket follows the account, not the network path.
     */
    @Bean("accountKeyResolver")
    @Primary
    KeyResolver accountKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                // TASK-MONO-370. Was `"acct:" + getSubject()` — a token with no `sub` string-
                // concatenated to the literal key "acct:null", merging every such caller into one
                // synthetic shared bucket. flatMap + justOrEmpty makes "no usable identity" empty
                // (map cannot: Reactor NPEs when a map lambda returns null), so it falls through to
                // the IP key — the coarse-but-correct answer for an unidentifiable caller.
                .flatMap(token -> Mono.justOrEmpty(token.getToken().getSubject()))
                .filter(subject -> !subject.isBlank())
                .map(subject -> buildKey(resolveRouteId(exchange), "acct:" + subject))
                .switchIfEmpty(Mono.fromSupplier(
                        () -> buildKey(resolveRouteId(exchange), resolveClientIp(exchange))));
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

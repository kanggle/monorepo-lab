package com.example.fanplatform.gateway.config;

import com.example.fanplatform.gateway.ratelimit.FailOpenRateLimiter;
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

@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    static final String UNKNOWN_IP = "unknown";
    static final String UNKNOWN_ROUTE = "unknown";
    /** fan-platform key prefix per Failure Scenarios — avoids cross-project collisions. */
    static final String KEY_PREFIX = "rate:fan-platform";

    /**
     * Key resolver used for unauthenticated requests. Produces
     * {@code rate:fan-platform:<routeId>:<clientIp>}.
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
     * Key resolver for authenticated requests — keyed on the JWT subject (account id).
     * Falls back to the IP-based key when no security context is present (e.g. public
     * routes / pre-auth phase).
     *
     * <p>{@code @Primary} designates this as the default {@link KeyResolver} injected by
     * Spring Cloud Gateway's {@code requestRateLimiterGatewayFilterFactory}. Without it,
     * the factory's constructor-injection fails with an ambiguous-bean error because both
     * {@code clientIpKeyResolver} and this bean implement the same interface. All
     * application.yml routes already reference this bean explicitly via SpEL
     * ({@code key-resolver: "#{@accountKeyResolver}"}); {@code @Primary} only resolves
     * the factory-level injection ambiguity — not the per-route resolver selection.
     * {@code clientIpKeyResolver} is kept as a named bean for potential future use on
     * public/unauthenticated routes that require pure IP-based bucketing.
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
     * Primary {@link RateLimiter} exposed to Spring Cloud Gateway. Wraps the
     * autoconfigured {@link RedisRateLimiter} with fail-open semantics: on Redis
     * connectivity errors, requests are allowed through with a WARN log + metric.
     * Rate limiting is a soft protection, not a correctness boundary — see
     * {@code platform/api-gateway-policy.md}.
     */
    @Bean
    @Primary
    RateLimiter<RedisRateLimiter.Config> failOpenRateLimiter(RedisRateLimiter delegate,
                                                             MeterRegistry meterRegistry) {
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

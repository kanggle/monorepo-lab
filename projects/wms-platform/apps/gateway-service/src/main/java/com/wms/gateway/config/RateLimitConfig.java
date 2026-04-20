package com.wms.gateway.config;

import com.wms.gateway.ratelimit.FailOpenRateLimiter;
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
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    static final String UNKNOWN_IP = "unknown";
    static final String UNKNOWN_ROUTE = "unknown";

    /**
     * Key resolver scoped to {@code (clientIp, routeId)} so that each route maintains an
     * independent rate-limit bucket per client. The built-in {@code RedisRateLimiter}
     * configured in {@code application.yml} consumes this key to meter the bucket.
     * <p>
     * Client IP resolution order: {@code X-Forwarded-For} first value → remote address →
     * {@code unknown}. Route id is pulled from
     * {@link ServerWebExchangeUtils#GATEWAY_ROUTE_ATTR}; when missing (e.g. pre-routing),
     * {@code unknown} is used and a WARN is logged — never throw NPE on resolution.
     */
    @Bean("clientIpKeyResolver")
    KeyResolver clientIpKeyResolver() {
        return exchange -> Mono.just(resolveClientIp(exchange) + ":" + resolveRouteId(exchange));
    }

    /**
     * Primary {@link RateLimiter} exposed to Spring Cloud Gateway. Wraps the
     * autoconfigured {@link RedisRateLimiter} with fail-open semantics: on Redis
     * connectivity errors, requests are allowed through with a WARN log. Rate limiting
     * is a soft protection, not a correctness boundary — see {@code api-gateway-policy.md}.
     */
    @Bean
    @Primary
    RateLimiter<RedisRateLimiter.Config> failOpenRateLimiter(RedisRateLimiter delegate) {
        return new FailOpenRateLimiter(delegate);
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

package com.wms.gateway.config;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /**
     * Key resolver scoped to {@code (clientIp, routeId)}. The built-in
     * {@code RedisRateLimiter} configured in {@code application.yml} consumes
     * this key to meter the bucket.
     * <p>
     * Client IP resolution order: {@code X-Forwarded-For} first value →
     * remote address → {@code unknown}.
     */
    @Bean("clientIpKeyResolver")
    KeyResolver clientIpKeyResolver() {
        return exchange -> Mono.just(resolveClientIp(exchange));
    }

    private static String resolveClientIp(org.springframework.web.server.ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma < 0 ? forwarded.trim() : forwarded.substring(0, comma).trim();
        }
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(addr -> addr.getHostAddress())
                .orElse("unknown");
    }
}

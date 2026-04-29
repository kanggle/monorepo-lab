package com.wms.gateway.filter;

import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds verified identity headers derived from the authenticated JWT:
 * {@code X-User-Id} ← {@code sub}, {@code X-User-Role} ← {@code role}/{@code roles},
 * {@code X-User-Email} ← {@code email}, {@code X-Actor-Id} ← {@code sub}.
 * <p>
 * Runs after Spring Security has populated the security context. If no JWT is
 * present (public routes), the filter becomes a no-op.
 */
@Component
public class JwtHeaderEnrichmentFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(token -> enrich(exchange, token.getToken()))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    private ServerWebExchange enrich(ServerWebExchange exchange, Jwt jwt) {
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String role = resolveRole(jwt);

        ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
        if (subject != null) {
            builder.header("X-User-Id", subject);
            builder.header("X-Actor-Id", subject);
        }
        if (email != null) {
            builder.header("X-User-Email", email);
        }
        // Always set X-User-Role. When no role claim is present, emit "" (empty
        // string) — downstream services must treat this as "no authorized role"
        // and deny access; leaving the header absent would let a buggy service
        // fall through to a default.
        builder.header("X-User-Role", role);
        String accountType = jwt.getClaimAsString("account_type");
        if (accountType != null) {
            builder.header("X-Account-Type", accountType);
        }
        return exchange.mutate().request(builder.build()).build();
    }

    /**
     * Resolves a role claim with defined precedence:
     * {@code roles} (array, joined on {@code ","}) → {@code role} (string) → {@code ""}.
     * Never returns {@code null}; callers can write the result directly to a header.
     */
    private String resolveRole(Jwt jwt) {
        Collection<String> multi = jwt.getClaimAsStringList("roles");
        if (multi != null && !multi.isEmpty()) {
            return multi.stream().collect(Collectors.joining(","));
        }
        Object single = jwt.getClaim("role");
        if (single instanceof String s && !s.isBlank()) {
            return s;
        }
        return "";
    }

    @Override
    public int getOrder() {
        // Runs after Spring Security's auth filter (which is around HIGHEST + 100)
        // but before the route-routing filter.
        return -1;
    }
}

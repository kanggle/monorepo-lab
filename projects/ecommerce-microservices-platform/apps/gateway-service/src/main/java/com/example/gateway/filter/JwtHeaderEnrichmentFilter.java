package com.example.gateway.filter;

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
 * Adds verified identity headers derived from the authenticated JWT (ADR-MONO-035 4b-2a):
 * {@code X-User-Id} ← {@code sub}, {@code X-User-Email} ← {@code email},
 * {@code X-User-Role} ← {@code roles} array (comma-joined) or {@code role} string,
 * {@code X-Tenant-Id} ← {@code tenant_id} (multi-tenant context propagation,
 * ADR-MONO-030 § 2.2 M2 layer 2).
 * <p>
 * {@code X-Account-Type} is no longer injected — no downstream service reads it
 * (verified ADR-032 D3), and the {@link IdentityHeaderStripFilter} strip entry remains
 * as inert defense-in-depth.
 * <p>
 * Runs after Spring Security has populated the security context. If no JWT is
 * present (public routes), the filter becomes a no-op. Any client-supplied copies
 * of these headers are removed first by {@link IdentityHeaderStripFilter}.
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
        }
        if (email != null) {
            builder.header("X-User-Email", email);
        }
        // Always set X-User-Role. When no role claim is present, emit "" (empty
        // string) — downstream services must treat this as "no authorized role".
        builder.header("X-User-Role", role);

        // Multi-tenant context propagation (ADR-MONO-030 § 2.2 M2 layer 2): downstream
        // services read X-Tenant-Id as the request's tenant context. The gate has
        // already rejected blank/missing tenant_id, but guard defensively so a public
        // (no-tenant) token never injects an empty context.
        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId != null && !tenantId.isBlank()) {
            builder.header("X-Tenant-Id", tenantId);
        }

        return exchange.mutate().request(builder.build()).build();
    }

    /**
     * Resolves a role claim with defined precedence:
     * {@code roles} (array, joined on {@code ","}) → {@code role} (string) → {@code ""}.
     * Never returns {@code null}.
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
        return -1;
    }
}

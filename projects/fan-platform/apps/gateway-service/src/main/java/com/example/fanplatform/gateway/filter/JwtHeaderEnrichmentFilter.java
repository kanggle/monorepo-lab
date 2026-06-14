package com.example.fanplatform.gateway.filter;

import com.example.fanplatform.gateway.security.TenantClaimValidator;
import java.util.Collection;
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
 * Adds verified identity headers derived from the authenticated JWT before the
 * request is routed downstream:
 *
 * <ul>
 *   <li>{@code X-User-Id} ← {@code sub}</li>
 *   <li>{@code X-Account-Id} ← {@code sub} (alias used by fan-platform downstream services)</li>
 *   <li>{@code X-Actor-Id} ← {@code sub}</li>
 *   <li>{@code X-User-Email} ← {@code email}</li>
 *   <li>{@code X-User-Role} / {@code X-Roles} ← {@code role}/{@code roles}</li>
 *   <li>{@code X-Tenant-Id} ← {@code tenant_id}</li>
 * </ul>
 *
 * <p>{@code X-Account-Type} is no longer injected (ADR-MONO-035 4b-2a / ADR-032 D3) —
 * no downstream service reads it; the {@code IdentityHeaderStripFilter} strip entry
 * remains as inert defense-in-depth.
 *
 * <p>This filter satisfies both the "TenantGateFilter" and "HeaderEnrichmentFilter"
 * roles described in TASK-FAN-BE-001. Tenant gating itself is enforced upstream
 * by {@link TenantClaimValidator} during JWT decoding — by the time this filter
 * runs the security context already contains a token with an acceptable
 * {@code tenant_id} (either {@code fan-platform} or the SUPER_ADMIN wildcard).
 *
 * <p>Runs after Spring Security has populated the security context. If no JWT is
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
        String tenantId = jwt.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID);
        String role = resolveRole(jwt);

        ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
        if (subject != null) {
            builder.header("X-User-Id", subject);
            builder.header("X-Account-Id", subject);
            builder.header("X-Actor-Id", subject);
        }
        if (email != null) {
            builder.header("X-User-Email", email);
        }
        if (tenantId != null && !tenantId.isBlank()) {
            builder.header("X-Tenant-Id", tenantId);
        }
        // Always set X-User-Role / X-Roles. When no role claim is present, emit ""
        // (empty string) — downstream services must treat this as "no authorized
        // role" and deny access; leaving the header absent would let a buggy
        // service fall through to a default.
        builder.header("X-User-Role", role);
        builder.header("X-Roles", role);
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
            return String.join(",", multi);
        }
        Object single = jwt.getClaim("role");
        if (single instanceof String s && !s.isBlank()) {
            return s;
        }
        return "";
    }

    @Override
    public int getOrder() {
        // Runs after Spring Security's auth filter (around HIGHEST + 100)
        // but before the route-routing filter.
        return -1;
    }
}

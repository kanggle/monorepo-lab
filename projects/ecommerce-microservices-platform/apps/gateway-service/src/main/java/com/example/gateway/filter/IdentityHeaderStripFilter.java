package com.example.gateway.filter;

import java.util.Set;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Strips client-supplied identity headers before any downstream trust is
 * established. Runs at the highest precedence so the header enrichment filter
 * sees a clean request and can set these headers from verified JWT claims.
 * <p>
 * Per {@code platform/api-gateway-policy.md} § Identity Header Handling — this
 * is a security boundary; incorrect ordering creates an impersonation vector.
 * <p>
 * Stripping is the <em>only</em> defence for headers the enrichment filter does
 * not re-set, and the only defence at all on public routes: {@link
 * JwtHeaderEnrichmentFilter} short-circuits to a no-op when no JWT is present,
 * so on {@code GET /api/products/**}, the carrier webhook, and every other
 * permitted path a forged header would otherwise reach the backend untouched.
 * Enrichment is defence in depth, not a substitute (TASK-BE-501).
 */
@Component
public class IdentityHeaderStripFilter implements GlobalFilter, Ordered {

    private static final Set<String> IDENTITY_HEADERS = Set.of(
            "X-User-Id",
            "X-User-Email",
            "X-User-Role",
            // Audit actor. Nothing in ecommerce reads it today, but the gateway must
            // still refuse to forward a client-supplied value: the policy names it
            // explicitly, and wms already trusts this exact header as the audit actor
            // in 12 controllers. A reader appearing here later must not silently
            // inherit an impersonation vector (TASK-BE-501).
            "X-Actor-Id",
            "X-Account-Type",
            "X-Tenant-Id"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> IDENTITY_HEADERS.forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

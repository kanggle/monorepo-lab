package com.wms.gateway.filter;

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
 * established. Runs at the highest precedence so the JWT filter / header
 * enrichment filter sees a clean request and can set these headers from
 * verified JWT claims.
 * <p>
 * Per {@code platform/api-gateway-policy.md} § Identity Header Handling — this
 * is a security boundary; incorrect ordering creates an impersonation vector.
 */
@Component
public class IdentityHeaderStripFilter implements GlobalFilter, Ordered {

    private static final Set<String> IDENTITY_HEADERS = Set.of(
            "X-User-Id",
            "X-User-Email",
            "X-User-Role",
            "X-Actor-Id",
            "X-Account-Type",
            // Named by specs/services/gateway-service/overview.md § Responsibilities but
            // absent from this set until TASK-BE-502. Nothing in wms reads them yet, and
            // JwtHeaderEnrichmentFilter does not re-set them — so a forged X-Tenant-Id
            // would have crossed the edge intact, which is precisely the tenant boundary
            // ADR-MONO-024 D2 exists to hold. Strip is the whole defence here; the first
            // controller to read one of these must not inherit a hole.
            "X-Account-Id",
            "X-Tenant-Id",
            "X-Roles"
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

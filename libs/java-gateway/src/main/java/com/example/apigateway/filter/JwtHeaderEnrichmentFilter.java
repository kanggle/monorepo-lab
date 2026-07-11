package com.example.apigateway.filter;

import com.example.apigateway.security.ReactiveJwtAccess;
import java.util.List;
import java.util.Objects;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Writes verified identity headers derived from the authenticated JWT, immediately after
 * {@link IdentityHeaderStripFilter} has removed whatever the client claimed.
 *
 * <p>The two filters are one mechanism: strip establishes that no header is trusted,
 * enrichment re-establishes the ones the gateway can vouch for. Reversing their order is
 * an impersonation vector, which is why {@code GatewayFilterOrderingTest} pins it in every
 * consumer.
 *
 * <p>Which headers get written is a per-domain decision, supplied as a list of
 * {@link JwtHeaderMapping}s at the wiring site (ADR-MONO-048 § D3 — the injected-header set
 * is the parameter). The filter itself holds only the parts that were identical across
 * wms / scm / fan: where the JWT comes from, that a request without one is a no-op, and
 * that it runs before routing.
 *
 * <p>On a public route there is no JWT, so no header is written — and none can be forged
 * either, because the strip filter already ran. Enrichment is not the defence there;
 * strip is.
 */
public class JwtHeaderEnrichmentFilter implements GlobalFilter, Ordered {

    private final List<JwtHeaderMapping> mappings;

    public JwtHeaderEnrichmentFilter(List<JwtHeaderMapping> mappings) {
        this.mappings = List.copyOf(Objects.requireNonNull(mappings, "mappings"));
    }

    /** The mappings this filter will apply, in order. Exposed so consumers can assert their policy. */
    public List<JwtHeaderMapping> mappings() {
        return mappings;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveJwtAccess.currentJwt()
                .map(jwt -> enrich(exchange, jwt))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    private ServerWebExchange enrich(ServerWebExchange exchange, Jwt jwt) {
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
        for (JwtHeaderMapping mapping : mappings) {
            String value = mapping.extractor().apply(jwt);
            if (mapping.writes(value)) {
                // An ALWAYS mapping whose extractor yielded null still writes the header, as
                // the empty string. Same reasoning as JwtClaims#role: a downstream service
                // that sees "" must deny, whereas one that sees nothing at all may fall
                // through to a default — the failure would be silent, and it would be open.
                builder.header(mapping.header(), value == null ? "" : value);
            }
        }
        return exchange.mutate().request(builder.build()).build();
    }

    @Override
    public int getOrder() {
        // After Spring Security's authentication filter (around HIGHEST_PRECEDENCE + 100)
        // — the security context must already hold the token — but before route routing.
        return -1;
    }
}

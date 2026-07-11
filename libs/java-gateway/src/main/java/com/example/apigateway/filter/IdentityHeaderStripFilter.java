package com.example.apigateway.filter;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Strips client-supplied identity headers before any downstream trust is established.
 *
 * <p>Runs at the highest precedence so that {@link JwtHeaderEnrichmentFilter} sees a clean
 * request and can set these headers from verified JWT claims. Per
 * {@code platform/api-gateway-policy.md} § Identity Header Handling this is a security
 * boundary: incorrect ordering creates an impersonation vector.
 *
 * <p>On routes that carry no JWT at all (HMAC-authenticated webhooks, public endpoints)
 * enrichment is a no-op and cannot act as a backstop. <strong>Stripping is the entire
 * defence there</strong> — which is why the strip set may not be narrowed.
 *
 * <h2>The strip set is add-only, and that is enforced by this type</h2>
 *
 * A consumer supplies <em>additional</em> headers; there is no constructor that replaces
 * {@link #BASELINE_HEADERS} and no setter that removes one. The asymmetry is deliberate
 * (ADR-MONO-048 § D3): removing a header from the set is exactly the defect
 * {@code TASK-BE-501}/{@code 502} fixed — wms was not stripping {@code X-Tenant-Id} that its
 * own spec named, so a forged one crossed the edge intact. An API that let a domain
 * subtract from the baseline would reopen that hole with nicer syntax, and the reopening
 * would look like configuration rather than like a vulnerability.
 */
public class IdentityHeaderStripFilter implements GlobalFilter, Ordered {

    /**
     * The headers every gateway strips. This is the union of what wms, scm and fan stripped
     * after {@code TASK-BE-501}/{@code 502} converged them — no domain stripped fewer, so
     * adopting the union changes nothing and locks in the floor.
     */
    public static final Set<String> BASELINE_HEADERS = Set.of(
            "X-User-Id",
            "X-User-Email",
            "X-User-Role",
            "X-Actor-Id",
            "X-Account-Id",
            "X-Tenant-Id",
            "X-Roles",
            "X-Account-Type");

    private final Set<String> strippedHeaders;

    /** Strips exactly {@link #BASELINE_HEADERS}. */
    public IdentityHeaderStripFilter() {
        this(Set.of());
    }

    /** Strips {@link #BASELINE_HEADERS} ∪ {@code additionalHeaders}. Never fewer. */
    public IdentityHeaderStripFilter(Set<String> additionalHeaders) {
        Objects.requireNonNull(additionalHeaders, "additionalHeaders");
        Set<String> union = new LinkedHashSet<>(BASELINE_HEADERS);
        union.addAll(additionalHeaders);
        this.strippedHeaders = Set.copyOf(union);
    }

    /** The headers this filter removes. Exposed so a consumer can assert its own set. */
    public Set<String> strippedHeaders() {
        return strippedHeaders;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> strippedHeaders.forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

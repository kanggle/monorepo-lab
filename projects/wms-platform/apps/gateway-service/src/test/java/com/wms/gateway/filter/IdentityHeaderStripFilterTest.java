package com.wms.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class IdentityHeaderStripFilterTest {

    private final IdentityHeaderStripFilter filter = new IdentityHeaderStripFilter();

    @Test
    void stripsAllClientSuppliedIdentityHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses")
                .header("X-User-Id", "client-injected-id")
                .header("X-User-Email", "forged@example.com")
                .header("X-User-Role", "MASTER_ADMIN")
                .header("X-Actor-Id", "forged-actor")
                .header("X-Account-Type", "OPERATOR")
                .header("X-Account-Id", "forged-account")
                .header("X-Tenant-Id", "victim-tenant")
                .header("X-Roles", "ADMIN")
                .header("Authorization", "Bearer xyz")
                .header("X-Request-Id", "req-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.captured).isNotNull();
        ServerHttpRequest forwarded = chain.captured.getRequest();
        assertThat(forwarded.getHeaders().get("X-User-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-User-Email")).isNull();
        assertThat(forwarded.getHeaders().get("X-User-Role")).isNull();
        assertThat(forwarded.getHeaders().get("X-Actor-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-Account-Type")).isNull();
        assertThat(forwarded.getHeaders().get("X-Account-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-Tenant-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-Roles")).isNull();
        // Non-identity headers remain untouched
        assertThat(forwarded.getHeaders().getFirst("Authorization")).isEqualTo("Bearer xyz");
        assertThat(forwarded.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-123");
    }

    /**
     * The three headers {@code specs/services/gateway-service/overview.md} §
     * Responsibilities names by hand — and which this filter did not actually strip until
     * TASK-BE-502. Asserted as a named group so the spec citation stays attached to the
     * assertion.
     * <p>
     * {@link JwtHeaderEnrichmentFilter} does not re-set any of them, so stripping is the
     * entire defence. A forged {@code X-Tenant-Id} crossing the edge is exactly the
     * tenant boundary ADR-MONO-024 D2 exists to hold.
     */
    @Test
    void stripsEveryHeaderNamedByTheGatewaySpec() {
        List<String> specHeaders = List.of("X-Account-Id", "X-Tenant-Id", "X-Roles");

        MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.get("/api/v1/master/warehouses");
        specHeaders.forEach(h -> builder.header(h, "forged"));
        CapturingChain chain = new CapturingChain();

        filter.filter(MockServerWebExchange.from(builder.build()), chain).block();

        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        // getFirst is case-insensitive, so this holds regardless of how the header was cased.
        specHeaders.forEach(h -> assertThat(forwarded.getFirst(h)).as(h).isNull());
    }

    /**
     * The webhook routes ({@code /webhooks/erp/**}) bypass the JWT filter entirely — they
     * authenticate by HMAC downstream. Enrichment is therefore a no-op there and cannot be
     * offered as a backstop: strip is all there is.
     */
    @Test
    void stripsForgedHeadersOnRoutesThatCarryNoJwt() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/webhooks/erp/inbound")
                .header("X-Actor-Id", "victim-operator-uuid")
                .header("X-Tenant-Id", "victim-tenant")
                .build();
        CapturingChain chain = new CapturingChain();

        filter.filter(MockServerWebExchange.from(request), chain).block();

        ServerHttpRequest forwarded = chain.captured.getRequest();
        assertThat(forwarded.getHeaders().get("X-Actor-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-Tenant-Id")).isNull();
    }

    @Test
    void runsAtHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }

    private static final class CapturingChain implements GatewayFilterChain {
        ServerWebExchange captured;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.captured = exchange;
            return Mono.empty();
        }
    }
}

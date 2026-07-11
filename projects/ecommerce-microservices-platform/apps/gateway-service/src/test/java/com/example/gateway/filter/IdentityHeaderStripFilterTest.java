package com.example.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class IdentityHeaderStripFilterTest {

    private final IdentityHeaderStripFilter filter = new IdentityHeaderStripFilter();

    @Test
    void stripsAllIdentityHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/123")
                .header("X-User-Id", "client-injected-id")
                .header("X-User-Email", "forged@example.com")
                .header("X-User-Role", "ECOMMERCE_OPERATOR")
                .header("X-Actor-Id", "forged-actor")
                .header("X-Account-Type", "OPERATOR")
                .header("X-Tenant-Id", "forged-tenant")
                .header("Authorization", "Bearer xyz")
                .header("X-Request-Id", "req-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = chain.captured.getRequest();
        assertThat(forwarded.getHeaders().get("X-User-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-User-Email")).isNull();
        assertThat(forwarded.getHeaders().get("X-User-Role")).isNull();
        assertThat(forwarded.getHeaders().get("X-Actor-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-Account-Type")).isNull();
        assertThat(forwarded.getHeaders().get("X-Tenant-Id")).isNull();
        // Non-identity headers remain untouched
        assertThat(forwarded.getHeaders().getFirst("Authorization")).isEqualTo("Bearer xyz");
        assertThat(forwarded.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-123");
    }

    /**
     * The four headers {@code platform/api-gateway-policy.md} § Identity Header Handling
     * names by hand. Asserting them as a group (rather than only inside the happy-path
     * test above) keeps the policy citation attached to the assertion: if the policy
     * grows a fifth header, this is the test that should fail.
     */
    @Test
    void stripsEveryHeaderNamedByThePlatformPolicy() {
        List<String> policyHeaders = List.of("X-User-Id", "X-User-Role", "X-User-Email", "X-Actor-Id");

        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/orders/1");
        policyHeaders.forEach(h -> builder.header(h, "forged"));
        CapturingChain chain = new CapturingChain();

        filter.filter(MockServerWebExchange.from(builder.build()), chain).block();

        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        // getFirst is case-insensitive, so this holds regardless of how the header was cased.
        policyHeaders.forEach(h -> assertThat(forwarded.getFirst(h)).as(h).isNull());
    }

    /**
     * The regression this filter exists to prevent (TASK-BE-501).
     * <p>
     * {@code X-Actor-Id} is the header wms trusts as the audit actor in 12 controllers.
     * ecommerce's {@link JwtHeaderEnrichmentFilter} never sets it, so stripping is the
     * <em>only</em> thing standing between a client-supplied value and the backend —
     * and on a public route there is no JWT at all, so enrichment is a no-op and cannot
     * be argued as a backstop. A forged actor id reaching a backend that logs it is
     * audit-log forgery.
     */
    @Test
    void stripsForgedActorIdOnAPublicRouteWhereEnrichmentCannotBackstopIt() {
        // GET /api/products/** is permitAll in SecurityConfig — no JWT, no enrichment.
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/42")
                .header("X-Actor-Id", "victim-operator-uuid")
                .build();
        CapturingChain chain = new CapturingChain();

        filter.filter(MockServerWebExchange.from(request), chain).block();

        assertThat(chain.captured.getRequest().getHeaders().get("X-Actor-Id")).isNull();
    }

    @Test
    void stripsIdentityHeadersEvenWhenSomeAbsent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/42")
                .header("X-User-Id", "evil-user")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = chain.captured.getRequest();
        assertThat(forwarded.getHeaders().get("X-User-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-User-Email")).isNull();
        assertThat(forwarded.getHeaders().get("X-User-Role")).isNull();
        assertThat(forwarded.getHeaders().get("X-Account-Type")).isNull();
    }

    @Test
    void passesRequestWithNoIdentityHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/search/products?q=shoes")
                .header("Accept", "application/json")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.captured).isNotNull();
        assertThat(chain.captured.getRequest().getHeaders().getFirst("Accept"))
                .isEqualTo("application/json");
    }

    @Test
    void runsAtHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
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

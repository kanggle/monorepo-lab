package com.example.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
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
                .header("X-User-Role", "ADMIN")
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
        assertThat(forwarded.getHeaders().get("X-Account-Type")).isNull();
        assertThat(forwarded.getHeaders().get("X-Tenant-Id")).isNull();
        // Non-identity headers remain untouched
        assertThat(forwarded.getHeaders().getFirst("Authorization")).isEqualTo("Bearer xyz");
        assertThat(forwarded.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-123");
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

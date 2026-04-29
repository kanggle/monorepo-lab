package com.wms.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
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
        // Non-identity headers remain untouched
        assertThat(forwarded.getHeaders().getFirst("Authorization")).isEqualTo("Bearer xyz");
        assertThat(forwarded.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-123");
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

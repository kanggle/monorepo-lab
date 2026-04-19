package com.wms.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesUuidWhenHeaderAbsent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        String forwarded = chain.captured.getRequest().getHeaders().getFirst("X-Request-Id");
        assertThat(forwarded).isNotNull();
        assertThat(UUID.fromString(forwarded)).isNotNull();

        String onResponse = exchange.getResponse().getHeaders().getFirst("X-Request-Id");
        assertThat(onResponse).isEqualTo(forwarded);
    }

    @Test
    void echoesClientSuppliedHeader() {
        String clientId = "client-correlation-42";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses")
                .header("X-Request-Id", clientId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.captured.getRequest().getHeaders().getFirst("X-Request-Id"))
                .isEqualTo(clientId);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id"))
                .isEqualTo(clientId);
    }

    @Test
    void runsAfterIdentityStrip() {
        IdentityHeaderStripFilter strip = new IdentityHeaderStripFilter();
        assertThat(filter.getOrder()).isGreaterThan(strip.getOrder());
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

package com.example.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.testfixtures.RecordingGatewayFilterChain;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesUuidWhenHeaderAbsent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/things").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        RecordingGatewayFilterChain chain = new RecordingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        String forwarded = chain.capturedExchange().getRequest().getHeaders().getFirst("X-Request-Id");
        assertThat(forwarded).isNotNull();
        assertThat(UUID.fromString(forwarded)).isNotNull();

        String onResponse = exchange.getResponse().getHeaders().getFirst("X-Request-Id");
        assertThat(onResponse).isEqualTo(forwarded);
    }

    @Test
    void echoesClientSuppliedHeader() {
        String clientId = "client-correlation-42";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/things")
                .header("X-Request-Id", clientId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        RecordingGatewayFilterChain chain = new RecordingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.capturedExchange().getRequest().getHeaders().getFirst("X-Request-Id"))
                .isEqualTo(clientId);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id"))
                .isEqualTo(clientId);
    }

    /**
     * The consuming gateways' identity-strip filter runs at {@code HIGHEST_PRECEDENCE}
     * and must come first, so this filter's order is pinned rather than merely
     * "some number". The cross-boundary assertion — strip &lt; requestId &lt; retryAfter
     * over the *real* filters — lives in each gateway's {@code GatewayFilterOrderingTest},
     * because the strip filter is still per-domain until ADR-MONO-048 D7 step 2.
     */
    @Test
    void runsJustAfterHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
        assertThat(filter.getOrder()).isGreaterThan(Ordered.HIGHEST_PRECEDENCE);
    }
}

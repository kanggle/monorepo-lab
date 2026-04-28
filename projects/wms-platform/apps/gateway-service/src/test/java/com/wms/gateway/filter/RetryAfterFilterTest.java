package com.wms.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RetryAfterFilterTest {

    private final RetryAfterFilter filter = new RetryAfterFilter();

    @Test
    void addsRetryAfterOnRateLimitedResponseViaSetComplete() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return ex.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After"))
                .isEqualTo("1");
    }

    @Test
    void doesNotOverrideExistingRetryAfterHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            ex.getResponse().getHeaders().set("Retry-After", "5");
            return ex.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After"))
                .isEqualTo("5");
    }

    @Test
    void doesNotAddRetryAfterOnSuccessResponse() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return ex.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After"))
                .isNull();
    }

    @Test
    void runsAfterRequestIdFilter() {
        RequestIdFilter requestId = new RequestIdFilter();
        assertThat(filter.getOrder()).isGreaterThan(requestId.getOrder());
    }
}

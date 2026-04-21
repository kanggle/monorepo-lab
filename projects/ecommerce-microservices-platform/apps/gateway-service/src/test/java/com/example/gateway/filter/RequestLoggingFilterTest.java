package com.example.gateway.filter;

import com.example.gateway.config.GatewayMetrics;
import com.example.gateway.security.RouteService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("RequestLoggingFilter 단위 테스트")
class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;
    private GatewayMetrics gatewayMetrics;
    private RouteService routeService;

    @BeforeEach
    void setUp() {
        gatewayMetrics = spy(new GatewayMetrics(new SimpleMeterRegistry()));
        routeService = spy(new RouteService());
        filter = new RequestLoggingFilter(gatewayMetrics, routeService);
    }

    @Test
    @DisplayName("5xx 응답 시 RouteService를 통해 대상 서비스를 해석하고 upstream error 메트릭을 기록한다")
    void filter_5xxResponse_incrementsUpstreamErrorWithRouteService() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(routeService).resolveTargetService("/api/users/me");
        verify(gatewayMetrics).incrementUpstreamError("user-service");
    }

    @Test
    @DisplayName("admin 경로 5xx 응답 시 올바른 서비스로 메트릭을 기록한다")
    void filter_adminPath5xx_incrementsCorrectService() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/products/42").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(routeService).resolveTargetService("/api/admin/products/42");
        verify(gatewayMetrics).incrementUpstreamError("product-service");
    }

    @Test
    @DisplayName("admin/users 경로 5xx 응답 시 user-service로 메트릭을 기록한다")
    void filter_adminUsersPath5xx_incrementsUserService() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/users").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(routeService).resolveTargetService("/api/admin/users");
        verify(gatewayMetrics).incrementUpstreamError("user-service");
    }

    @Test
    @DisplayName("정상 응답 시 upstream error 메트릭을 기록하지 않는다")
    void filter_successResponse_doesNotIncrementUpstreamError() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/1").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(gatewayMetrics, never()).incrementUpstreamError(anyString());
    }

    @Test
    @DisplayName("429 응답 시 rate_limited 메트릭을 기록한다")
    void filter_429Response_incrementsRateLimited() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(routeService).resolveTargetService("/api/orders/123");
        verify(gatewayMetrics).incrementRateLimited("order-service");
    }

    @Test
    @DisplayName("정상 응답 시 rate_limited 메트릭을 기록하지 않는다")
    void filter_successResponse_doesNotIncrementRateLimited() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/1").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(gatewayMetrics, never()).incrementRateLimited(anyString());
    }

    @Test
    @DisplayName("401 응답 시 rate_limited 메트릭을 기록하지 않는다")
    void filter_401Response_doesNotIncrementRateLimited() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(gatewayMetrics, never()).incrementRateLimited(anyString());
    }

    @Test
    @DisplayName("필터 순서는 -99이다")
    void getOrder_returnsMinusNinetyNine() {
        assertThat(filter.getOrder()).isEqualTo(-99);
    }
}

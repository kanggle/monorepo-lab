package com.example.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestIdFilter 단위 테스트")
class RequestIdFilterUnitTest {

    @Mock
    private GatewayFilterChain chain;

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
    }

    @Test
    @DisplayName("X-Request-ID가 없으면 새로 생성")
    void filter_noRequestId_generatesNew() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange captured = captor.getValue();
        String requestId = captured.getRequest().getHeaders().getFirst("X-Request-ID");
        assertThat(requestId).isNotNull().isNotBlank();
        // UUID format check
        assertThat(requestId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("X-Request-ID가 있으면 그대로 전파")
    void filter_existingRequestId_propagates() {
        String existingId = "existing-request-id-123";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Request-ID", existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange captured = captor.getValue();
        String requestId = captured.getRequest().getHeaders().getFirst("X-Request-ID");
        assertThat(requestId).isEqualTo(existingId);
    }

    @Test
    @DisplayName("응답 헤더에도 X-Request-ID가 설정됨")
    void filter_setsResponseHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        String responseRequestId = captor.getValue().getResponse().getHeaders().getFirst("X-Request-ID");
        assertThat(responseRequestId).isNotNull().isNotBlank();
    }
}

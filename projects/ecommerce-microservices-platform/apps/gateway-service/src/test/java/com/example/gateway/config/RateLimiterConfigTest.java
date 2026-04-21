package com.example.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimiterConfig 단위 테스트")
class RateLimiterConfigTest {

    private RateLimiterConfig config;
    private KeyResolver keyResolver;

    @BeforeEach
    void setUp() {
        config = new RateLimiterConfig();
        keyResolver = config.ipKeyResolver();
    }

    @Test
    @DisplayName("ipKeyResolver가 null이 아닌 빈을 반환한다")
    void ipKeyResolver_returnsNonNull() {
        assertThat(keyResolver).isNotNull();
    }

    @Test
    @DisplayName("요청의 원격 주소에서 IP를 추출한다")
    void ipKeyResolver_validRemoteAddress_extractsIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/123")
                .remoteAddress(new InetSocketAddress("192.168.1.100", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String key = keyResolver.resolve(exchange).block();

        assertThat(key).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("서로 다른 IP에서 오는 요청은 다른 키를 반환한다")
    void ipKeyResolver_differentIps_returnsDifferentKeys() {
        MockServerHttpRequest request1 = MockServerHttpRequest.get("/api/orders/1")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 1111))
                .build();
        MockServerHttpRequest request2 = MockServerHttpRequest.get("/api/orders/2")
                .remoteAddress(new InetSocketAddress("10.0.0.2", 2222))
                .build();

        String key1 = keyResolver.resolve(MockServerWebExchange.from(request1)).block();
        String key2 = keyResolver.resolve(MockServerWebExchange.from(request2)).block();

        assertThat(key1).isNotEqualTo(key2);
    }
}

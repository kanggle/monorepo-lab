package com.example.batch.infrastructure.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link OrderServiceClient#existingOrderIds} (TASK-INT-028). The cases that matter are the ones
 * where order-service does not give a real answer: each must throw, because the job releases the
 * coupon of every order absent from the result.
 */
@DisplayName("OrderServiceClient — 주문 존재 조회 (TASK-INT-028)")
class OrderServiceClientExistenceTest {

    private MockWebServer orderService;
    private OrderServiceClient client;

    @BeforeEach
    void setUp() throws IOException {
        orderService = new MockWebServer();
        orderService.start();
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        client = new OrderServiceClient(
                "http://" + orderService.getHostName() + ":" + orderService.getPort(), 30, 200, tokenProvider);
    }

    @AfterEach
    void tearDown() throws IOException {
        orderService.shutdown();
    }

    @Test
    @DisplayName("Bearer 와 id 목록을 보내고 존재 집합을 돌려준다")
    void sendsBearerAndIds_returnsExistingSet() throws Exception {
        orderService.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"existingOrderIds\":[\"o-1\"]}"));

        Set<String> existing = client.existingOrderIds(List.of("o-1", "o-2"));

        assertThat(existing).containsExactly("o-1");
        RecordedRequest request = orderService.takeRequest();
        assertThat(request.getPath()).isEqualTo(OrderServiceClient.EXISTENCE_PATH);
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-jwt");
        assertThat(request.getBody().readUtf8()).contains("\"o-1\"").contains("\"o-2\"");
    }

    @Test
    @DisplayName("🔴 본문 없는 200 은 «아무 주문도 없음» 이 아니라 예외다")
    void emptyBody_throws_neverReadsAsNoneExist() {
        orderService.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client.existingOrderIds(List.of("o-1"))).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("🔴 existingOrderIds 필드가 없는 200 도 예외다")
    void missingField_throws() {
        orderService.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{}"));

        assertThatThrownBy(() -> client.existingOrderIds(List.of("o-1"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("5xx 는 예외다")
    void serverError_throws() {
        orderService.enqueue(new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> client.existingOrderIds(List.of("o-1"))).isInstanceOf(RuntimeException.class);
    }
}

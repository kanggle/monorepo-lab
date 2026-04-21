package com.example.review.infrastructure.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("OrderServiceClient 단위 테스트")
class OrderServiceClientUnitTest {

    private static final String BASE_URL = "http://test-order-service";

    private OrderServiceClient orderServiceClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient testRestClient = builder.build();

        orderServiceClient = new OrderServiceClient(BASE_URL);

        Field field = OrderServiceClient.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(orderServiceClient, testRestClient);
    }

    @Test
    @DisplayName("구매 확인 응답이 true이면 true를 반환한다")
    void hasUserPurchasedProduct_purchased_returnsTrue() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        mockServer.expect(requestTo(BASE_URL + "/api/orders/verify-purchase?productId=" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Id", userId.toString()))
                .andRespond(withSuccess("{\"purchased\":true}", MediaType.APPLICATION_JSON));

        boolean result = orderServiceClient.hasUserPurchasedProduct(userId, productId);

        assertThat(result).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("구매 확인 응답이 false이면 false를 반환한다")
    void hasUserPurchasedProduct_notPurchased_returnsFalse() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        mockServer.expect(requestTo(BASE_URL + "/api/orders/verify-purchase?productId=" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Id", userId.toString()))
                .andRespond(withSuccess("{\"purchased\":false}", MediaType.APPLICATION_JSON));

        boolean result = orderServiceClient.hasUserPurchasedProduct(userId, productId);

        assertThat(result).isFalse();
        mockServer.verify();
    }

    @Test
    @DisplayName("order-service 호출 실패 시 RuntimeException을 던진다")
    void hasUserPurchasedProduct_serverError_throwsRuntimeException() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        mockServer.expect(requestTo(BASE_URL + "/api/orders/verify-purchase?productId=" + productId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> orderServiceClient.hasUserPurchasedProduct(userId, productId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Purchase verification failed");

        mockServer.verify();
    }
}

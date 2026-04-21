package com.example.shipping;

import com.example.shipping.infrastructure.event.OrderConfirmedEventConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("배송 서비스 통합 테스트")
class ShippingIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shipping_db")
            .withUsername("shipping_user")
            .withPassword("shipping_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderConfirmedEventConsumer orderConfirmedEventConsumer;

    @Autowired
    private ObjectMapper objectMapper;

    private String buildOrderConfirmedEventJson(String orderId, String userId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "event_id", UUID.randomUUID().toString(),
                "event_type", "OrderConfirmed",
                "occurred_at", Instant.now().toString(),
                "source", "order-service",
                "payload", Map.of(
                        "orderId", orderId,
                        "userId", userId,
                        "confirmedAt", Instant.now().toString()
                )
        ));
    }

    @Test
    @DisplayName("OrderConfirmed 이벤트 수신 -> 배송 생성 -> 주문 ID로 조회 성공")
    void orderConfirmed_createsShipping_queryByOrderId() throws Exception {
        String orderId = "order-" + System.nanoTime();
        String userId = "user-" + System.nanoTime();

        // 1. OrderConfirmed 이벤트 소비
        orderConfirmedEventConsumer.onMessage(buildOrderConfirmedEventJson(orderId, userId));

        // 2. 주문 ID로 배송 조회
        mockMvc.perform(get("/api/shippings/orders/" + orderId)
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.statusHistory").isArray())
                .andExpect(jsonPath("$.statusHistory[0].status").value("PREPARING"));
    }

    @Test
    @DisplayName("배송 상태 업데이트 (PREPARING -> SHIPPED)")
    void updateStatus_preparingToShipped_success() throws Exception {
        String orderId = "order-ship-" + System.nanoTime();
        String userId = "user-ship-" + System.nanoTime();

        orderConfirmedEventConsumer.onMessage(buildOrderConfirmedEventJson(orderId, userId));

        // Get shipping ID
        MvcResult getResult = mockMvc.perform(get("/api/shippings/orders/" + orderId)
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andReturn();

        String shippingId = objectMapper.readTree(getResult.getResponse().getContentAsString())
                .get("shippingId").asText();

        // Update status
        String updateBody = """
                {
                    "status": "SHIPPED",
                    "trackingNumber": "TRK-12345",
                    "carrier": "CJ대한통운"
                }
                """;

        mockMvc.perform(put("/api/shippings/" + shippingId + "/status")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingId").value(shippingId))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("배송 상태 전체 전이: PREPARING -> SHIPPED -> IN_TRANSIT -> DELIVERED")
    void fullStatusTransition_success() throws Exception {
        String orderId = "order-full-" + System.nanoTime();
        String userId = "user-full-" + System.nanoTime();

        orderConfirmedEventConsumer.onMessage(buildOrderConfirmedEventJson(orderId, userId));

        MvcResult getResult = mockMvc.perform(get("/api/shippings/orders/" + orderId)
                        .header("X-User-Id", userId))
                .andReturn();

        String shippingId = objectMapper.readTree(getResult.getResponse().getContentAsString())
                .get("shippingId").asText();

        // PREPARING -> SHIPPED
        mockMvc.perform(put("/api/shippings/" + shippingId + "/status")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SHIPPED", "trackingNumber": "TRK-999", "carrier": "한진택배"}
                                """))
                .andExpect(status().isOk());

        // SHIPPED -> IN_TRANSIT
        mockMvc.perform(put("/api/shippings/" + shippingId + "/status")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "IN_TRANSIT"}
                                """))
                .andExpect(status().isOk());

        // IN_TRANSIT -> DELIVERED
        mockMvc.perform(put("/api/shippings/" + shippingId + "/status")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DELIVERED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // Verify full history
        mockMvc.perform(get("/api/shippings/orders/" + orderId)
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.trackingNumber").value("TRK-999"))
                .andExpect(jsonPath("$.carrier").value("한진택배"))
                .andExpect(jsonPath("$.statusHistory.length()").value(4));
    }

    @Test
    @DisplayName("역방향 전이 시도 시 422 반환")
    void updateStatus_backwardTransition_returns422() throws Exception {
        String orderId = "order-back-" + System.nanoTime();
        String userId = "user-back-" + System.nanoTime();

        orderConfirmedEventConsumer.onMessage(buildOrderConfirmedEventJson(orderId, userId));

        MvcResult getResult = mockMvc.perform(get("/api/shippings/orders/" + orderId)
                        .header("X-User-Id", userId))
                .andReturn();

        String shippingId = objectMapper.readTree(getResult.getResponse().getContentAsString())
                .get("shippingId").asText();

        // PREPARING -> SHIPPED
        mockMvc.perform(put("/api/shippings/" + shippingId + "/status")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SHIPPED", "trackingNumber": "TRK-111", "carrier": "롯데택배"}
                                """))
                .andExpect(status().isOk());

        // SHIPPED -> PREPARING (역방향)
        mockMvc.perform(put("/api/shippings/" + shippingId + "/status")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PREPARING"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("다른 사용자가 배송 조회 시 403 반환")
    void getShipping_differentUser_returns403() throws Exception {
        String orderId = "order-auth-" + System.nanoTime();
        String userId = "user-auth-" + System.nanoTime();

        orderConfirmedEventConsumer.onMessage(buildOrderConfirmedEventJson(orderId, userId));

        mockMvc.perform(get("/api/shippings/orders/" + orderId)
                        .header("X-User-Id", "other-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("존재하지 않는 배송 조회 시 404 반환")
    void getShipping_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/shippings/orders/nonexistent-order")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHIPPING_NOT_FOUND"));
    }

    @Test
    @DisplayName("관리자 배송 목록 조회 성공")
    void listShippings_admin_returnsPage() throws Exception {
        String orderId = "order-list-" + System.nanoTime();
        String userId = "user-list-" + System.nanoTime();

        orderConfirmedEventConsumer.onMessage(buildOrderConfirmedEventJson(orderId, userId));

        mockMvc.perform(get("/api/shippings")
                        .header("X-User-Role", "ADMIN")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("비관리자가 관리자 API 호출 시 403 반환")
    void listShippings_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/shippings")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("중복 OrderConfirmed 이벤트 수신 시 멱등 처리")
    void orderConfirmed_duplicate_idempotent() throws Exception {
        String orderId = "order-dup-" + System.nanoTime();
        String userId = "user-dup-" + System.nanoTime();
        String eventJson = buildOrderConfirmedEventJson(orderId, userId);

        orderConfirmedEventConsumer.onMessage(eventJson);

        // 동일 이벤트 재처리 - 예외 없어야 함
        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> orderConfirmedEventConsumer.onMessage(eventJson));

        // 여전히 조회 가능
        mockMvc.perform(get("/api/shippings/orders/" + orderId)
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId));
    }

    @Test
    @DisplayName("SHIPPED 전이 시 trackingNumber 없으면 400 반환")
    void updateStatus_shippedWithoutTrackingNumber_returns400() throws Exception {
        String orderId = "order-trk-" + System.nanoTime();
        String userId = "user-trk-" + System.nanoTime();

        orderConfirmedEventConsumer.onMessage(buildOrderConfirmedEventJson(orderId, userId));

        MvcResult getResult = mockMvc.perform(get("/api/shippings/orders/" + orderId)
                        .header("X-User-Id", userId))
                .andReturn();

        String shippingId = objectMapper.readTree(getResult.getResponse().getContentAsString())
                .get("shippingId").asText();

        mockMvc.perform(put("/api/shippings/" + shippingId + "/status")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SHIPPED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SHIPPING_REQUEST"));
    }
}

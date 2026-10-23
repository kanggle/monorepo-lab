package com.example.shipping;

import com.example.shipping.infrastructure.event.OrderCancelledEventConsumer;
import com.example.shipping.infrastructure.event.OrderConfirmedEventConsumer;
import com.example.shipping.infrastructure.event.WmsShippingConfirmedConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ShippingServiceApplication.class, properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("Fulfillment 정방향/역방향 통합 테스트 (ADR-MONO-022 §D7)")
class FulfillmentIntegrationTest {

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
    private WmsShippingConfirmedConsumer wmsShippingConfirmedConsumer;

    @Autowired
    private OrderCancelledEventConsumer orderCancelledEventConsumer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String orderConfirmedJson(String orderId, String userId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "event_id", UUID.randomUUID().toString(),
                "event_type", "OrderConfirmed",
                "occurred_at", Instant.now().toString(),
                "source", "order-service",
                "payload", Map.of(
                        "orderId", orderId,
                        "userId", userId,
                        "confirmedAt", Instant.now().toString(),
                        "lines", List.of(Map.of(
                                "sku", "v1", "productId", "p1", "variantId", "v1", "quantity", 2)),
                        "shippingAddress", Map.of(
                                "recipientName", "홍길동",
                                "address", "서울특별시 강남구 테헤란로 1 101호",
                                "phone", "010-1234-5678"))
        ));
    }

    private String orderCancelledJson(String orderId, String userId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "event_id", UUID.randomUUID().toString(),
                "event_type", "OrderCancelled",
                "occurred_at", "2026-10-23T10:00:01Z",
                "source", "order-service",
                "tenant_id", "ecommerce",
                "payload", Map.of(
                        "orderId", orderId,
                        "userId", userId,
                        "cancelledAt", "2026-10-23T10:00:00Z",
                        "cancelReason", "OPERATOR")
        ));
    }

    private String wmsConfirmedJson(String orderNo) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "outbound.shipping.confirmed",
                "occurredAt", "2026-06-08T15:00:00Z",
                "aggregateType", "outbound",
                "aggregateId", "wms-internal-1",
                "payload", Map.of(
                        "orderId", "wms-internal-1",
                        "orderNo", orderNo,
                        "shipmentNo", "SHP-20260608-0001",
                        "carrierCode", "CJ-LOGISTICS",
                        "shippedAt", "2026-06-08T15:00:00Z")
        ));
    }

    @Test
    @DisplayName("정방향: OrderConfirmed -> Shipping PREPARING + fulfillment outbox row(camelCase) 작성")
    void forward_orderConfirmed_writesFulfillmentOutboxRow() throws Exception {
        String orderId = "order-fwd-" + System.nanoTime();
        String userId = "user-fwd-" + System.nanoTime();

        orderConfirmedEventConsumer.onMessage(orderConfirmedJson(orderId, userId));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM shipping_outbox WHERE aggregate_id = ? AND event_type = 'FulfillmentRequested'", orderId);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("aggregate_type")).isEqualTo("Fulfillment");

        JsonNode envelope = objectMapper.readTree((String) rows.get(0).get("payload"));
        assertThat(envelope.get("eventType").asText()).isEqualTo("ecommerce.fulfillment.requested");
        assertThat(envelope.get("aggregateType").asText()).isEqualTo("fulfillment");
        assertThat(envelope.get("aggregateId").asText()).isEqualTo(orderId);
        assertThat(envelope.has("eventId")).isTrue();
        assertThat(envelope.has("occurredAt")).isTrue();

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("orderNo").asText()).isEqualTo(orderId);
        assertThat(payload.get("customerPartnerCode").asText()).isEqualTo("ECOMMERCE-STORE");
        assertThat(payload.get("warehouseCode").asText()).isEqualTo("WH-MAIN");
        assertThat(payload.get("shipTo").get("recipientName").asText()).isEqualTo("홍길동");
        assertThat(payload.get("lines").get(0).get("lineNo").asInt()).isEqualTo(1);
        assertThat(payload.get("lines").get(0).get("skuCode").asText()).isEqualTo("v1");
        assertThat(payload.get("lines").get(0).get("qtyOrdered").asInt()).isEqualTo(2);

        // TASK-MONO-305: a forward-published order is persisted wms_routed=TRUE (same tx).
        Boolean wmsRouted = jdbcTemplate.queryForObject(
                "SELECT wms_routed FROM shippings WHERE order_id = ?", Boolean.class, orderId);
        assertThat(wmsRouted).isTrue();
    }

    @Test
    @DisplayName("manual-confirm: wmsRouted 주문을 deductWmsInventory=true 로 SHIPPED 전환 시 manual-confirm outbox row 작성")
    void manualConfirm_wmsRoutedShipped_writesManualConfirmOutboxRow() throws Exception {
        String orderId = "order-mc-" + System.nanoTime();
        String userId = "user-mc-" + System.nanoTime();

        // OrderConfirmed → Shipping PREPARING, wms_routed=TRUE (fulfillment enabled by default).
        orderConfirmedEventConsumer.onMessage(orderConfirmedJson(orderId, userId));

        String shippingId = jdbcTemplate.queryForObject(
                "SELECT shipping_id FROM shippings WHERE order_id = ?", String.class, orderId);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/shippings/" + shippingId + "/status")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_SHIPPING_READ,ECOMMERCE_SHIPPING_WRITE")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SHIPPED","trackingNumber":"TRK-MC-1","carrier":"CJ대한통운","deductWmsInventory":true}
                                """))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM shipping_outbox WHERE aggregate_id = ? AND event_type = 'ManualShipConfirmRequested'", orderId);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("aggregate_type")).isEqualTo("Shipping");

        JsonNode envelope = objectMapper.readTree((String) rows.get(0).get("payload"));
        assertThat(envelope.get("eventType").asText()).isEqualTo("ecommerce.shipping.manual-confirm-requested");
        assertThat(envelope.get("aggregateType").asText()).isEqualTo("shipping");
        assertThat(envelope.get("aggregateId").asText()).isEqualTo(orderId);
        assertThat(envelope.get("tenantId").asText()).isEqualTo("ecommerce");

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("orderNo").asText()).isEqualTo(orderId);
        assertThat(payload.get("carrierCode").asText()).isEqualTo("CJ대한통운");
        assertThat(payload.get("trackingNo").asText()).isEqualTo("TRK-MC-1");
    }

    @Test
    @DisplayName("manual-confirm: deductWmsInventory 미지정이면 manual-confirm outbox row 미작성 (기존 동작 유지)")
    void manualConfirm_deductOff_noManualConfirmOutboxRow() throws Exception {
        String orderId = "order-mcoff-" + System.nanoTime();
        String userId = "user-mcoff-" + System.nanoTime();

        orderConfirmedEventConsumer.onMessage(orderConfirmedJson(orderId, userId));

        String shippingId = jdbcTemplate.queryForObject(
                "SELECT shipping_id FROM shippings WHERE order_id = ?", String.class, orderId);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/shippings/" + shippingId + "/status")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_SHIPPING_READ,ECOMMERCE_SHIPPING_WRITE")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SHIPPED","trackingNumber":"TRK-MCOFF-1","carrier":"CJ대한통운"}
                                """))
                .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipping_outbox WHERE aggregate_id = ? AND event_type = 'ManualShipConfirmRequested'",
                Integer.class, orderId);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("역방향: wms shipping.confirmed -> Shipping PREPARING -> SHIPPED (tracking/carrier)")
    void return_wmsConfirmed_flipsShippingToShipped() throws Exception {
        String orderId = "order-ret-" + System.nanoTime();
        String userId = "user-ret-" + System.nanoTime();

        orderConfirmedEventConsumer.onMessage(orderConfirmedJson(orderId, userId));

        wmsShippingConfirmedConsumer.onMessage(wmsConfirmedJson(orderId));

        mockMvc.perform(get("/api/shippings/orders/" + orderId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.trackingNumber").value("SHP-20260608-0001"))
                .andExpect(jsonPath("$.carrier").value("CJ-LOGISTICS"));
    }

    @Test
    @DisplayName("역방향 멱등: wms shipping.confirmed 재전달 시 SHIPPED 유지 (no error)")
    void return_wmsConfirmed_idempotent() throws Exception {
        String orderId = "order-idem-" + System.nanoTime();
        String userId = "user-idem-" + System.nanoTime();

        orderConfirmedEventConsumer.onMessage(orderConfirmedJson(orderId, userId));
        wmsShippingConfirmedConsumer.onMessage(wmsConfirmedJson(orderId));

        // re-deliver a distinct eventId for the same order — already SHIPPED, must be a no-op
        org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(() ->
                wmsShippingConfirmedConsumer.onMessage(wmsConfirmedJson(orderId)));

        mockMvc.perform(get("/api/shippings/orders/" + orderId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    // ─── TASK-BE-649 — ecommerce-initiated cancel (ADR-MONO-022 D4 v2(d)) ──────────────

    private Integer outboxCount(String orderId, String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipping_outbox WHERE aggregate_id = ? AND event_type = ?",
                Integer.class, orderId, eventType);
    }

    @Test
    @DisplayName("BE-649: OrderCancelled → PREPARING+wmsRouted 배송 CANCELLED + cancel-requested outbox row (같은 tx), 상태 변경 이벤트 없음")
    void cancel_preparingRouted_cancelsAndWritesCancelRequestedRow() throws Exception {
        String orderId = "order-cxl-" + System.nanoTime();
        String userId = "user-cxl-" + System.nanoTime();
        orderConfirmedEventConsumer.onMessage(orderConfirmedJson(orderId, userId));

        orderCancelledEventConsumer.onMessage(orderCancelledJson(orderId, userId));

        mockMvc.perform(get("/api/shippings/orders/" + orderId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.statusHistory[1].status").value("CANCELLED"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM shipping_outbox WHERE aggregate_id = ? AND event_type = 'FulfillmentCancelRequested'",
                orderId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("aggregate_type")).isEqualTo("Fulfillment");

        JsonNode envelope = objectMapper.readTree((String) rows.get(0).get("payload"));
        assertThat(envelope.get("eventId").asText()).isEqualTo(rows.get(0).get("event_id").toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo("ecommerce.fulfillment.cancel-requested");
        assertThat(envelope.get("aggregateType").asText()).isEqualTo("fulfillment");
        assertThat(envelope.get("aggregateId").asText()).isEqualTo(orderId);
        assertThat(envelope.get("tenantId").asText()).isEqualTo("ecommerce");
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("orderNo").asText()).isEqualTo(orderId);
        assertThat(payload.get("tenantId").asText()).isEqualTo("ecommerce");
        assertThat(payload.get("reason").asText()).isEqualTo("ORDER_CANCELLED");
        assertThat(payload.get("requestedAt").asText()).isEqualTo("2026-10-23T10:00:00Z");

        assertThat(outboxCount(orderId, "ShippingStatusChanged")).isZero();
    }

    @Test
    @DisplayName("BE-649: OrderCancelled 재전달(다른 eventId) → 그대로 CANCELLED, cancel-requested 는 1 건")
    void cancel_redelivery_isIdempotent() throws Exception {
        String orderId = "order-cxl2-" + System.nanoTime();
        String userId = "user-cxl2-" + System.nanoTime();
        orderConfirmedEventConsumer.onMessage(orderConfirmedJson(orderId, userId));

        orderCancelledEventConsumer.onMessage(orderCancelledJson(orderId, userId));
        org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(() ->
                orderCancelledEventConsumer.onMessage(orderCancelledJson(orderId, userId)));

        assertThat(outboxCount(orderId, "FulfillmentCancelRequested")).isEqualTo(1);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM shippings WHERE order_id = ?", String.class, orderId);
        assertThat(status).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("BE-649: SHIPPED 배송에 OrderCancelled → no-op (반품 흐름), cancel-requested 없음")
    void cancel_shipped_isNoOp() throws Exception {
        String orderId = "order-cxl3-" + System.nanoTime();
        String userId = "user-cxl3-" + System.nanoTime();
        orderConfirmedEventConsumer.onMessage(orderConfirmedJson(orderId, userId));
        wmsShippingConfirmedConsumer.onMessage(wmsConfirmedJson(orderId));

        orderCancelledEventConsumer.onMessage(orderCancelledJson(orderId, userId));

        mockMvc.perform(get("/api/shippings/orders/" + orderId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
        assertThat(outboxCount(orderId, "FulfillmentCancelRequested")).isZero();
    }

    @Test
    @DisplayName("BE-649: CANCELLED 배송에 wms shipping.confirmed 가 와도 되살아나지 않는다 (예외 없음)")
    void cancel_thenWmsConfirmed_staysCancelled() throws Exception {
        String orderId = "order-cxl4-" + System.nanoTime();
        String userId = "user-cxl4-" + System.nanoTime();
        orderConfirmedEventConsumer.onMessage(orderConfirmedJson(orderId, userId));
        orderCancelledEventConsumer.onMessage(orderCancelledJson(orderId, userId));

        org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(() ->
                wmsShippingConfirmedConsumer.onMessage(wmsConfirmedJson(orderId)));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM shippings WHERE order_id = ?", String.class, orderId);
        assertThat(status).isEqualTo("CANCELLED");
        assertThat(outboxCount(orderId, "ShippingStatusChanged")).isZero();
    }

    @Test
    @DisplayName("BE-649: 운영자 PUT status=CANCELLED → 422 INVALID_STATUS_TRANSITION, 배송 그대로")
    void operatorPutCancelled_isRejected() throws Exception {
        String orderId = "order-cxl5-" + System.nanoTime();
        String userId = "user-cxl5-" + System.nanoTime();
        orderConfirmedEventConsumer.onMessage(orderConfirmedJson(orderId, userId));
        String shippingId = jdbcTemplate.queryForObject(
                "SELECT shipping_id FROM shippings WHERE order_id = ?", String.class, orderId);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/shippings/" + shippingId + "/status")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR,ECOMMERCE_SHIPPING_READ,ECOMMERCE_SHIPPING_WRITE")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM shippings WHERE order_id = ?", String.class, orderId);
        assertThat(status).isEqualTo("PREPARING");
        assertThat(outboxCount(orderId, "FulfillmentCancelRequested")).isZero();
    }
}

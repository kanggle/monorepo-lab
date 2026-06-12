package com.example.order.contract;

import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.event.OrderConfirmedEvent;
import com.example.order.application.event.OrderPlacedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static com.example.order.contract.ContractTestHelper.assertFieldsMatch;

/**
 * order-service 이벤트 스키마 컨트랙트 검증 테스트.
 * 검증 근거: specs/contracts/events/order-events.md
 */
@DisplayName("Order Event 컨트랙트 테스트 — specs/contracts/events/order-events.md")
class OrderEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SPEC_REF = "specs/contracts/events/order-events.md";
    private static final Set<String> ENVELOPE_FIELDS = Set.of("event_id", "event_type", "occurred_at", "source", "tenant_id", "payload");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-25T12:00:00Z"), ZoneOffset.UTC);

    // ─── OrderPlaced ────────────────────────────────────────────────────

    @Test
    @DisplayName("OrderPlaced envelope은 스펙 정의 필드만 포함한다")
    void orderPlaced_envelope_matchesSpec() throws Exception {
        OrderPlacedEvent event = OrderPlacedEvent.of(
                "order-1", "user-1", 30000L,
                List.of(new OrderPlacedEvent.Item("p1", "v1", 2, 15000L)),
                new OrderPlacedEvent.ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시", "강남구"),
                FIXED_CLOCK
        );

        String json = objectMapper.writeValueAsString(event);
        assertFieldsMatch(json, ENVELOPE_FIELDS, SPEC_REF + " envelope");
    }

    @Test
    @DisplayName("OrderPlaced payload는 스펙 정의 필드만 포함한다")
    void orderPlaced_payload_matchesSpec() throws Exception {
        OrderPlacedEvent event = OrderPlacedEvent.of(
                "order-1", "user-1", 30000L,
                List.of(new OrderPlacedEvent.Item("p1", "v1", 2, 15000L)),
                new OrderPlacedEvent.ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시", "강남구"),
                FIXED_CLOCK
        );

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(event));
        JsonNode payload = root.get("payload");

        assertFieldsMatch(payload, Set.of("orderId", "userId", "totalPrice", "items", "shippingAddress"),
                SPEC_REF + " OrderPlaced payload");

        JsonNode item = payload.get("items").get(0);
        assertFieldsMatch(item, Set.of("productId", "variantId", "quantity", "unitPrice", "sellerId"),
                SPEC_REF + " OrderPlaced payload items[]");

        JsonNode addr = payload.get("shippingAddress");
        assertFieldsMatch(addr, Set.of("recipient", "phone", "zipCode", "address1", "address2"),
                SPEC_REF + " OrderPlaced payload shippingAddress");
    }

    // ─── OrderConfirmed ─────────────────────────────────────────────────

    @Test
    @DisplayName("OrderConfirmed envelope은 스펙 정의 필드만 포함한다")
    void orderConfirmed_envelope_matchesSpec() throws Exception {
        OrderConfirmedEvent event = OrderConfirmedEvent.of(
                "order-1", "user-1", Instant.parse("2026-06-08T10:00:00Z"),
                List.of(new OrderConfirmedEvent.Line("v1", "p1", "v1", 2)),
                new OrderConfirmedEvent.ShippingAddress("홍길동", "서울시 강남구 101호", "010-1234-5678"),
                FIXED_CLOCK
        );

        String json = objectMapper.writeValueAsString(event);
        assertFieldsMatch(json, ENVELOPE_FIELDS, SPEC_REF + " envelope");
    }

    @Test
    @DisplayName("OrderConfirmed payload는 스펙 정의 필드만 포함한다 (lines/shippingAddress 추가)")
    void orderConfirmed_payload_matchesSpec() throws Exception {
        OrderConfirmedEvent event = OrderConfirmedEvent.of(
                "order-1", "user-1", Instant.parse("2026-06-08T10:00:00Z"),
                List.of(new OrderConfirmedEvent.Line("v1", "p1", "v1", 2)),
                new OrderConfirmedEvent.ShippingAddress("홍길동", "서울시 강남구 101호", "010-1234-5678"),
                FIXED_CLOCK
        );

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(event));
        JsonNode payload = root.get("payload");

        assertFieldsMatch(payload, Set.of("orderId", "userId", "confirmedAt", "lines", "shippingAddress"),
                SPEC_REF + " OrderConfirmed payload");

        JsonNode line = payload.get("lines").get(0);
        assertFieldsMatch(line, Set.of("sku", "productId", "variantId", "quantity"),
                SPEC_REF + " OrderConfirmed payload lines[]");

        JsonNode addr = payload.get("shippingAddress");
        assertFieldsMatch(addr, Set.of("recipientName", "address", "phone"),
                SPEC_REF + " OrderConfirmed payload shippingAddress");
    }

    // ─── OrderCancelled ─────────────────────────────────────────────────

    @Test
    @DisplayName("OrderCancelled envelope은 스펙 정의 필드만 포함한다")
    void orderCancelled_envelope_matchesSpec() throws Exception {
        OrderCancelledEvent event = OrderCancelledEvent.of("order-1", "user-1", Instant.parse("2026-03-25T12:00:00Z"), FIXED_CLOCK);

        String json = objectMapper.writeValueAsString(event);
        assertFieldsMatch(json, ENVELOPE_FIELDS, SPEC_REF + " envelope");
    }

    @Test
    @DisplayName("OrderCancelled payload는 {orderId, userId, cancelledAt}만 포함한다")
    void orderCancelled_payload_matchesSpec() throws Exception {
        OrderCancelledEvent event = OrderCancelledEvent.of("order-1", "user-1", Instant.parse("2026-03-25T12:00:00Z"), FIXED_CLOCK);

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(event));
        JsonNode payload = root.get("payload");

        assertFieldsMatch(payload, Set.of("orderId", "userId", "cancelledAt"),
                SPEC_REF + " OrderCancelled payload");
    }
}

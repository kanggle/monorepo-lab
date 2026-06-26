package com.example.payment.contract;

import com.example.payment.application.event.PaymentCompletedEvent;
import com.example.payment.application.event.PaymentRefundUnresolvedEvent;
import com.example.payment.application.event.PaymentRefundedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.example.payment.contract.ContractTestHelper.assertFieldsMatch;

/**
 * payment-service 이벤트 스키마 컨트랙트 검증 테스트.
 * 검증 근거: specs/contracts/events/payment-events.md
 *
 * TASK-BE-400: envelope 에 tenant_id 추가 (additive). ENVELOPE_FIELDS 에 포함.
 */
@DisplayName("Payment Event 컨트랙트 테스트 — specs/contracts/events/payment-events.md")
class PaymentEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SPEC_REF = "specs/contracts/events/payment-events.md";
    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "event_id", "event_type", "occurred_at", "source", "tenant_id", "payload"
    );

    // ─── PaymentCompleted ───────────────────────────────────────────────

    @Test
    @DisplayName("PaymentCompleted envelope은 스펙 정의 필드만 포함한다 (tenant_id 포함)")
    void paymentCompleted_envelope_matchesSpec() throws Exception {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                "evt-1", "PaymentCompleted", "2026-03-25T00:00:00Z", "payment-service",
                "ecommerce",
                new PaymentCompletedEvent.Payload("pay-1", "order-1", "user-1", 30000L, "2026-03-25T00:00:00Z")
        );

        assertFieldsMatch(objectMapper.writeValueAsString(event), ENVELOPE_FIELDS, SPEC_REF + " envelope");
    }

    @Test
    @DisplayName("PaymentCompleted payload는 {paymentId, orderId, userId, amount, paidAt}만 포함한다")
    void paymentCompleted_payload_matchesSpec() throws Exception {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                "evt-1", "PaymentCompleted", "2026-03-25T00:00:00Z", "payment-service",
                "ecommerce",
                new PaymentCompletedEvent.Payload("pay-1", "order-1", "user-1", 30000L, "2026-03-25T00:00:00Z")
        );

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payload, Set.of("paymentId", "orderId", "userId", "amount", "paidAt"),
                SPEC_REF + " PaymentCompleted payload");
    }

    // ─── PaymentRefunded ────────────────────────────────────────────────

    @Test
    @DisplayName("PaymentRefunded envelope은 스펙 정의 필드만 포함한다 (tenant_id 포함)")
    void paymentRefunded_envelope_matchesSpec() throws Exception {
        PaymentRefundedEvent event = new PaymentRefundedEvent(
                "evt-2", "PaymentRefunded", "2026-03-25T00:00:00Z", "payment-service",
                "ecommerce",
                new PaymentRefundedEvent.Payload("pay-1", "order-1", "user-1", 30000L, 30000L, true,
                        "2026-03-25T01:00:00Z")
        );

        assertFieldsMatch(objectMapper.writeValueAsString(event), ENVELOPE_FIELDS, SPEC_REF + " envelope");
    }

    @Test
    @DisplayName("PaymentRefunded payload는 {paymentId, orderId, userId, amount, totalRefunded, fullyRefunded, refundedAt}만 포함한다")
    void paymentRefunded_payload_matchesSpec() throws Exception {
        PaymentRefundedEvent event = new PaymentRefundedEvent(
                "evt-2", "PaymentRefunded", "2026-03-25T00:00:00Z", "payment-service",
                "ecommerce",
                new PaymentRefundedEvent.Payload("pay-1", "order-1", "user-1", 10000L, 10000L, false,
                        "2026-03-25T01:00:00Z")
        );

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payload, Set.of("paymentId", "orderId", "userId", "amount",
                        "totalRefunded", "fullyRefunded", "refundedAt"),
                SPEC_REF + " PaymentRefunded payload");
    }

    // ─── PaymentRefundUnresolved (TASK-BE-438 terminal escalation) ───────

    @Test
    @DisplayName("PaymentRefundUnresolved envelope은 스펙 정의 필드만 포함한다 (tenant_id 포함)")
    void paymentRefundUnresolved_envelope_matchesSpec() throws Exception {
        PaymentRefundUnresolvedEvent event = new PaymentRefundUnresolvedEvent(
                "evt-3", "PaymentRefundUnresolved", "2026-06-26T03:00:00Z", "payment-service",
                "ecommerce",
                new PaymentRefundUnresolvedEvent.Payload("pay-1", "order-1", "pk_test_123", 30000L,
                        "PgGatewayUnavailableException", 8, "attempt cap exhausted", "2026-06-26T03:00:00Z")
        );

        assertFieldsMatch(objectMapper.writeValueAsString(event), ENVELOPE_FIELDS, SPEC_REF + " envelope");
    }

    @Test
    @DisplayName("PaymentRefundUnresolved payload는 {paymentId, orderId, paymentKey, amount, reason, attempts, lastError, occurredAt}만 포함한다")
    void paymentRefundUnresolved_payload_matchesSpec() throws Exception {
        PaymentRefundUnresolvedEvent event = new PaymentRefundUnresolvedEvent(
                "evt-3", "PaymentRefundUnresolved", "2026-06-26T03:00:00Z", "payment-service",
                "ecommerce",
                new PaymentRefundUnresolvedEvent.Payload("pay-1", "order-1", "pk_test_123", 30000L,
                        "PgGatewayUnavailableException", 8, "attempt cap exhausted", "2026-06-26T03:00:00Z")
        );

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payload, Set.of("paymentId", "orderId", "paymentKey", "amount",
                        "reason", "attempts", "lastError", "occurredAt"),
                SPEC_REF + " PaymentRefundUnresolved payload");
    }
}

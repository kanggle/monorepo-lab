package com.example.payment.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentRefundedEvent 직렬화 테스트")
class PaymentRefundedEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("envelope 필드가 snake_case로 직렬화된다 (tenant_id 포함)")
    void serialize_envelopeFields_areSnakeCase() throws Exception {
        PaymentRefundedEvent event = new PaymentRefundedEvent(
                "evt-456", "PaymentRefunded", "2026-03-24T00:00:00Z",
                "payment-service",
                "ecommerce",
                new PaymentRefundedEvent.Payload("pay-1", "order-1", "user-1", 30000L, "2026-03-24T00:00:00Z")
        );

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("\"event_id\"");
        assertThat(json).contains("\"event_type\"");
        assertThat(json).contains("\"occurred_at\"");
        assertThat(json).contains("\"tenant_id\"");
        assertThat(json).doesNotContain("\"eventId\"");
        assertThat(json).doesNotContain("\"eventType\"");
        assertThat(json).doesNotContain("\"occurredAt\"");
    }

    @Test
    @DisplayName("snake_case JSON에서 역직렬화된다 (tenant_id 포함)")
    void deserialize_snakeCaseJson_createsEvent() throws Exception {
        String json = """
                {
                  "event_id": "evt-456",
                  "event_type": "PaymentRefunded",
                  "occurred_at": "2026-03-24T00:00:00Z",
                  "source": "payment-service",
                  "tenant_id": "ecommerce",
                  "payload": {
                    "paymentId": "pay-1",
                    "orderId": "order-1",
                    "userId": "user-1",
                    "amount": 30000,
                    "refundedAt": "2026-03-24T00:00:00Z"
                  }
                }
                """;

        PaymentRefundedEvent event = objectMapper.readValue(json, PaymentRefundedEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-456");
        assertThat(event.eventType()).isEqualTo("PaymentRefunded");
        assertThat(event.occurredAt()).isEqualTo("2026-03-24T00:00:00Z");
        assertThat(event.source()).isEqualTo("payment-service");
        assertThat(event.tenantId()).isEqualTo("ecommerce");
        assertThat(event.payload().orderId()).isEqualTo("order-1");
    }

    @Test
    @DisplayName("tenant_id 없는 레거시 JSON 역직렬화 시 tenantId 가 null 이다 (backward-compatible)")
    void deserialize_legacyJsonWithoutTenantId_tenantIdIsNull() throws Exception {
        String json = """
                {
                  "event_id": "evt-old",
                  "event_type": "PaymentRefunded",
                  "occurred_at": "2026-01-01T00:00:00Z",
                  "source": "payment-service",
                  "payload": {
                    "paymentId": "pay-old",
                    "orderId": "order-old",
                    "userId": "user-old",
                    "amount": 10000,
                    "refundedAt": "2026-01-01T00:00:00Z"
                  }
                }
                """;

        PaymentRefundedEvent event = objectMapper.readValue(json, PaymentRefundedEvent.class);

        assertThat(event.tenantId()).isNull();
        assertThat(event.payload().paymentId()).isEqualTo("pay-old");
    }
}

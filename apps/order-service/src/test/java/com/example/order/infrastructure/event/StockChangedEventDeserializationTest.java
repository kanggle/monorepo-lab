package com.example.order.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StockChangedEvent 역직렬화 테스트")
class StockChangedEventDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("snake_case envelope JSON에서 역직렬화된다")
    void deserialize_snakeCaseEnvelope_succeeds() throws Exception {
        String json = """
                {
                  "event_id": "evt-123",
                  "event_type": "StockChanged",
                  "occurred_at": "2026-03-25T00:00:00Z",
                  "source": "product-service",
                  "payload": {
                    "productId": "prod-1",
                    "variantId": "var-1",
                    "previousStock": 100,
                    "currentStock": 150,
                    "delta": 50,
                    "reason": "RESTOCK",
                    "orderId": null
                  }
                }
                """;

        StockChangedEvent event = objectMapper.readValue(json, StockChangedEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-123");
        assertThat(event.eventType()).isEqualTo("StockChanged");
        assertThat(event.occurredAt()).isEqualTo("2026-03-25T00:00:00Z");
        assertThat(event.payload().productId()).isEqualTo("prod-1");
    }

    @Test
    @DisplayName("camelCase envelope JSON에서도 역직렬화된다 (하위 호환)")
    void deserialize_camelCaseEnvelope_backwardsCompatible() throws Exception {
        String json = """
                {
                  "eventId": "evt-123",
                  "eventType": "StockChanged",
                  "occurredAt": "2026-03-25T00:00:00Z",
                  "source": "product-service",
                  "payload": {
                    "productId": "prod-1",
                    "variantId": "var-1",
                    "previousStock": 100,
                    "currentStock": 150,
                    "delta": 50,
                    "reason": "RESTOCK",
                    "orderId": null
                  }
                }
                """;

        StockChangedEvent event = objectMapper.readValue(json, StockChangedEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-123");
        assertThat(event.eventType()).isEqualTo("StockChanged");
        assertThat(event.occurredAt()).isEqualTo("2026-03-25T00:00:00Z");
    }
}

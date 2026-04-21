package com.example.order.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderPlacedEvent 직렬화 테스트")
class OrderPlacedEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-25T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("envelope 필드가 snake_case로 직렬화된다")
    void serialize_envelopeFields_areSnakeCase() throws Exception {
        OrderPlacedEvent event = OrderPlacedEvent.of(
                "order-1", "user-1", 30000L,
                List.of(new OrderPlacedEvent.Item("prod-1", "var-1", 2, 15000L)),
                new OrderPlacedEvent.ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시", "강남구"),
                FIXED_CLOCK
        );

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("\"event_id\"");
        assertThat(json).contains("\"event_type\"");
        assertThat(json).contains("\"occurred_at\"");
        assertThat(json).doesNotContain("\"eventId\"");
        assertThat(json).doesNotContain("\"eventType\"");
        assertThat(json).doesNotContain("\"occurredAt\"");
    }

    @Test
    @DisplayName("snake_case JSON에서 역직렬화된다")
    void deserialize_snakeCaseJson_createsEvent() throws Exception {
        String json = """
                {
                  "event_id": "evt-123",
                  "event_type": "OrderPlaced",
                  "occurred_at": "2026-03-25T00:00:00Z",
                  "source": "order-service",
                  "payload": {
                    "orderId": "order-1",
                    "userId": "user-1",
                    "totalPrice": 30000,
                    "items": [{"productId": "prod-1", "variantId": "var-1", "quantity": 2, "unitPrice": 15000}],
                    "shippingAddress": {"recipient": "홍길동", "phone": "010-1234-5678", "zipCode": "12345", "address1": "서울시", "address2": "강남구"}
                  }
                }
                """;

        OrderPlacedEvent event = objectMapper.readValue(json, OrderPlacedEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-123");
        assertThat(event.eventType()).isEqualTo("OrderPlaced");
        assertThat(event.occurredAt()).isEqualTo("2026-03-25T00:00:00Z");
        assertThat(event.source()).isEqualTo("order-service");
        assertThat(event.payload().orderId()).isEqualTo("order-1");
    }
}

package com.example.order.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderCancelledEvent 직렬화 테스트")
class OrderCancelledEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-25T15:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("envelope 필드가 snake_case로 직렬화된다")
    void serialize_envelopeFields_areSnakeCase() throws Exception {
        Instant cancelledAt = Instant.parse("2026-03-25T12:00:00Z");
        OrderCancelledEvent event = OrderCancelledEvent.of("order-1", "user-1", cancelledAt, FIXED_CLOCK);

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
                  "event_id": "evt-456",
                  "event_type": "OrderCancelled",
                  "occurred_at": "2026-03-25T00:00:00Z",
                  "source": "order-service",
                  "payload": {
                    "orderId": "order-1",
                    "userId": "user-1",
                    "cancelledAt": "2026-03-25T00:00:00Z"
                  }
                }
                """;

        OrderCancelledEvent event = objectMapper.readValue(json, OrderCancelledEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-456");
        assertThat(event.eventType()).isEqualTo("OrderCancelled");
        assertThat(event.occurredAt()).isEqualTo("2026-03-25T00:00:00Z");
        assertThat(event.source()).isEqualTo("order-service");
        assertThat(event.payload().orderId()).isEqualTo("order-1");
    }
}

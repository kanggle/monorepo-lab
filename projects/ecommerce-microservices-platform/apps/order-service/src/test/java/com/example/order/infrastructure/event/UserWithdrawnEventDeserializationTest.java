package com.example.order.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserWithdrawnEvent 역직렬화 테스트 (order-service consumer)")
class UserWithdrawnEventDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("snake_case envelope JSON에서 역직렬화된다")
    void deserialize_snakeCaseEnvelope_succeeds() throws Exception {
        String json = """
                {
                  "event_id": "evt-789",
                  "event_type": "UserWithdrawn",
                  "occurred_at": "2026-03-25T00:00:00Z",
                  "source": "user-service",
                  "payload": {
                    "userId": "user-1",
                    "withdrawnAt": "2026-03-25T00:00:00Z"
                  }
                }
                """;

        UserWithdrawnEvent event = objectMapper.readValue(json, UserWithdrawnEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-789");
        assertThat(event.eventType()).isEqualTo("UserWithdrawn");
        assertThat(event.payload().userId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("camelCase envelope JSON에서도 역직렬화된다 (하위 호환)")
    void deserialize_camelCaseEnvelope_backwardsCompatible() throws Exception {
        String json = """
                {
                  "eventId": "evt-789",
                  "eventType": "UserWithdrawn",
                  "occurredAt": "2026-03-25T00:00:00Z",
                  "source": "user-service",
                  "payload": {
                    "userId": "user-1",
                    "withdrawnAt": "2026-03-25T00:00:00Z"
                  }
                }
                """;

        UserWithdrawnEvent event = objectMapper.readValue(json, UserWithdrawnEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-789");
        assertThat(event.eventType()).isEqualTo("UserWithdrawn");
    }

    /**
     * TASK-BE-533 regression — the real envelope always carries {@code tenant_id}
     * (specs/contracts/events/user-events.md § Event Envelope; ADR-MONO-030 Step 4 /
     * TASK-BE-367 M5, "always present, never blank"). Before {@code @JsonIgnoreProperties} was
     * added to {@code UserWithdrawnEvent}, this exact shape — which is what
     * {@code KafkaUserProfileEventPublisher} actually puts on the wire, and what
     * {@code knowledge/runbooks/user-withdrawn-not-cancelled.md} §3 tells on-call to hand-build —
     * threw {@code UnrecognizedPropertyException} and routed straight to the DLQ. This fixture is
     * not synthetic: it is the real production envelope shape, unlike the two fixtures above which
     * both omit the field the real producer always sends.
     */
    @Test
    @DisplayName("실제 운영 envelope(tenant_id 포함)에서도 역직렬화된다 — 미인식 필드는 무시한다")
    void deserialize_realEnvelopeWithTenantId_ignoresUnknownField() throws Exception {
        String json = """
                {
                  "event_id": "evt-789",
                  "event_type": "UserWithdrawn",
                  "occurred_at": "2026-03-25T00:00:00Z",
                  "source": "user-service",
                  "tenant_id": "ecommerce",
                  "payload": {
                    "userId": "user-1",
                    "withdrawnAt": "2026-03-25T00:00:00Z"
                  }
                }
                """;

        UserWithdrawnEvent event = objectMapper.readValue(json, UserWithdrawnEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-789");
        assertThat(event.payload().userId()).isEqualTo("user-1");
    }
}

package com.example.auth.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserWithdrawnEvent 역직렬화 테스트 (auth-service consumer)")
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
}

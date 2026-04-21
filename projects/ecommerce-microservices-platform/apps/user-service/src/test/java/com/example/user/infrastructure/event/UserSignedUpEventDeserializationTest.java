package com.example.user.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserSignedUpEvent 역직렬화 테스트 (user-service consumer)")
class UserSignedUpEventDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("snake_case envelope JSON에서 역직렬화된다")
    void deserialize_snakeCaseEnvelope_succeeds() throws Exception {
        String json = """
                {
                  "event_id": "550e8400-e29b-41d4-a716-446655440000",
                  "event_type": "UserSignedUp",
                  "occurred_at": 1711324800.000000000,
                  "source": "auth-service",
                  "payload": {
                    "userId": "550e8400-e29b-41d4-a716-446655440001",
                    "email": "test@example.com",
                    "name": "홍길동"
                  }
                }
                """;

        UserSignedUpEvent event = objectMapper.readValue(json, UserSignedUpEvent.class);

        assertThat(event.eventId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(event.eventType()).isEqualTo("UserSignedUp");
        assertThat(event.source()).isEqualTo("auth-service");
        assertThat(event.payload().email()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("camelCase envelope JSON에서도 역직렬화된다 (하위 호환)")
    void deserialize_camelCaseEnvelope_backwardsCompatible() throws Exception {
        String json = """
                {
                  "eventId": "550e8400-e29b-41d4-a716-446655440000",
                  "eventType": "UserSignedUp",
                  "occurredAt": 1711324800.000000000,
                  "source": "auth-service",
                  "payload": {
                    "userId": "550e8400-e29b-41d4-a716-446655440001",
                    "email": "test@example.com",
                    "name": "홍길동"
                  }
                }
                """;

        UserSignedUpEvent event = objectMapper.readValue(json, UserSignedUpEvent.class);

        assertThat(event.eventId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(event.eventType()).isEqualTo("UserSignedUp");
    }
}

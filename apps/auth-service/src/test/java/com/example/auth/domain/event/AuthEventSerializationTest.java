package com.example.auth.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthEvent 직렬화 테스트")
class AuthEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("envelope 필드가 snake_case로 직렬화된다")
    void serialize_envelopeFields_areSnakeCase() throws Exception {
        UserSignedUp payload = new UserSignedUp(UUID.randomUUID(), "test@example.com", "홍길동");
        AuthEvent event = AuthEvent.of(payload);

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
                  "event_id": "550e8400-e29b-41d4-a716-446655440000",
                  "event_type": "UserSignedUp",
                  "occurred_at": "2026-03-25T00:00:00Z",
                  "source": "auth-service",
                  "payload": {
                    "userId": "550e8400-e29b-41d4-a716-446655440001",
                    "email": "test@example.com",
                    "name": "홍길동"
                  }
                }
                """;

        AuthEvent event = objectMapper.readValue(json, AuthEvent.class);

        assertThat(event.eventId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(event.eventType()).isEqualTo("UserSignedUp");
        assertThat(event.source()).isEqualTo("auth-service");
    }
}

package com.example.user.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserWithdrawnEvent 직렬화 테스트")
class UserWithdrawnEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("envelope 필드가 snake_case로 직렬화된다")
    void serialize_envelopeFields_areSnakeCase() throws Exception {
        UserWithdrawnEvent event = new UserWithdrawnEvent(
                UUID.randomUUID(), "UserWithdrawn", Instant.now(), "user-service",
                new UserWithdrawnEvent.Payload(UUID.randomUUID(), Instant.now())
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
                  "event_id": "550e8400-e29b-41d4-a716-446655440000",
                  "event_type": "UserWithdrawn",
                  "occurred_at": 1711324800.000000000,
                  "source": "user-service",
                  "payload": {
                    "userId": "550e8400-e29b-41d4-a716-446655440001",
                    "withdrawnAt": 1711324800.000000000
                  }
                }
                """;

        UserWithdrawnEvent event = objectMapper.readValue(json, UserWithdrawnEvent.class);

        assertThat(event.eventId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(event.eventType()).isEqualTo("UserWithdrawn");
        assertThat(event.source()).isEqualTo("user-service");
        assertThat(event.payload().userId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
        assertThat(event.payload().withdrawnAt()).isNotNull();
    }

    @Test
    @DisplayName("payload 필드가 올바르게 직렬화된다")
    void serialize_payloadFields_areCorrect() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant withdrawnAt = Instant.now();
        UserWithdrawnEvent event = new UserWithdrawnEvent(
                UUID.randomUUID(), "UserWithdrawn", Instant.now(), "user-service",
                new UserWithdrawnEvent.Payload(userId, withdrawnAt)
        );

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("\"userId\"");
        assertThat(json).contains("\"withdrawnAt\"");
        assertThat(json).contains(userId.toString());
    }
}

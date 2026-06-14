package com.example.user.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserProfileUpdatedEvent 직렬화 테스트")
class UserProfileUpdatedEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("envelope 필드가 snake_case로 직렬화된다")
    void serialize_envelopeFields_areSnakeCase() throws Exception {
        UserProfileUpdatedEvent event = new UserProfileUpdatedEvent(
                UUID.randomUUID(), "UserProfileUpdated", Instant.now(), "user-service", "ecommerce",
                new UserProfileUpdatedEvent.Payload(UUID.randomUUID(), "닉네임", "010-1234-5678", null, Instant.now())
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
                  "event_type": "UserProfileUpdated",
                  "occurred_at": 1711324800.000000000,
                  "source": "user-service",
                  "payload": {
                    "userId": "550e8400-e29b-41d4-a716-446655440001",
                    "nickname": "닉네임",
                    "phone": "010-1234-5678",
                    "profileImageUrl": null,
                    "updatedAt": 1711324800.000000000
                  }
                }
                """;

        UserProfileUpdatedEvent event = objectMapper.readValue(json, UserProfileUpdatedEvent.class);

        assertThat(event.eventId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(event.eventType()).isEqualTo("UserProfileUpdated");
        assertThat(event.source()).isEqualTo("user-service");
    }
}

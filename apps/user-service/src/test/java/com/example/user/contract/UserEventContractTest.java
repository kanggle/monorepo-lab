package com.example.user.contract;

import com.example.user.infrastructure.event.UserProfileUpdatedEvent;
import com.example.user.infrastructure.event.UserWithdrawnEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static com.example.user.contract.ContractTestHelper.assertFieldsMatch;

/**
 * user-service 이벤트 스키마 컨트랙트 검증 테스트.
 * 검증 근거: specs/contracts/events/user-events.md
 */
@DisplayName("User Event 컨트랙트 테스트 — specs/contracts/events/user-events.md")
class UserEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String SPEC_REF = "specs/contracts/events/user-events.md";
    private static final Set<String> ENVELOPE_FIELDS = Set.of("event_id", "event_type", "occurred_at", "source", "payload");

    // ─── UserProfileUpdated ─────────────────────────────────────────────

    @Test
    @DisplayName("UserProfileUpdated envelope은 스펙 정의 필드만 포함한다")
    void userProfileUpdated_envelope_matchesSpec() throws Exception {
        UserProfileUpdatedEvent event = new UserProfileUpdatedEvent(
                UUID.randomUUID(), "UserProfileUpdated", Instant.now(), "user-service",
                new UserProfileUpdatedEvent.Payload(UUID.randomUUID(), "길동이", "010-1234-5678", "https://img.example.com/photo.jpg", Instant.now())
        );

        String json = objectMapper.writeValueAsString(event);
        assertFieldsMatch(json, ENVELOPE_FIELDS, SPEC_REF + " envelope");
    }

    @Test
    @DisplayName("UserProfileUpdated payload는 {userId, nickname, phone, profileImageUrl, updatedAt}만 포함한다")
    void userProfileUpdated_payload_matchesSpec() throws Exception {
        UserProfileUpdatedEvent event = new UserProfileUpdatedEvent(
                UUID.randomUUID(), "UserProfileUpdated", Instant.now(), "user-service",
                new UserProfileUpdatedEvent.Payload(UUID.randomUUID(), "길동이", "010-1234-5678", "https://img.example.com/photo.jpg", Instant.now())
        );

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payload, Set.of("userId", "nickname", "phone", "profileImageUrl", "updatedAt"),
                SPEC_REF + " UserProfileUpdated payload");
    }

    // ─── UserWithdrawn ───────────────────────────────────────────────────

    @Test
    @DisplayName("UserWithdrawn envelope은 스펙 정의 필드만 포함한다")
    void userWithdrawn_envelope_matchesSpec() throws Exception {
        UserWithdrawnEvent event = new UserWithdrawnEvent(
                UUID.randomUUID(), "UserWithdrawn", Instant.now(), "user-service",
                new UserWithdrawnEvent.Payload(UUID.randomUUID(), Instant.now())
        );

        String json = objectMapper.writeValueAsString(event);
        assertFieldsMatch(json, ENVELOPE_FIELDS, SPEC_REF + " envelope");
    }

    @Test
    @DisplayName("UserWithdrawn payload는 {userId, withdrawnAt}만 포함한다")
    void userWithdrawn_payload_matchesSpec() throws Exception {
        UserWithdrawnEvent event = new UserWithdrawnEvent(
                UUID.randomUUID(), "UserWithdrawn", Instant.now(), "user-service",
                new UserWithdrawnEvent.Payload(UUID.randomUUID(), Instant.now())
        );

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payload, Set.of("userId", "withdrawnAt"),
                SPEC_REF + " UserWithdrawn payload");
    }
}

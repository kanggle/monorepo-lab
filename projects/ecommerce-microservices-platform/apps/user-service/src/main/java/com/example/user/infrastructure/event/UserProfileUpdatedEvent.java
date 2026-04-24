package com.example.user.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record UserProfileUpdatedEvent(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") Instant occurredAt,
        String source,
        Payload payload
) {
    public record Payload(
            UUID userId,
            String nickname,
            String phone,
            String profileImageUrl,
            Instant updatedAt
    ) {}
}

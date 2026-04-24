package com.example.user.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record UserSignedUpEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") UUID eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") Instant occurredAt,
        String source,
        Payload payload
) {
    public record Payload(
            UUID userId,
            String email,
            String name
    ) {}
}

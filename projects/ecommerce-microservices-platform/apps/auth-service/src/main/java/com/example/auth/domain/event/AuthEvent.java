package com.example.auth.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record AuthEvent(
    @JsonProperty("event_id") UUID eventId,
    @JsonProperty("event_type") String eventType,
    @JsonProperty("occurred_at") Instant occurredAt,
    String source,
    Object payload
) {
    public static AuthEvent of(Object payload) {
        return new AuthEvent(
            UUID.randomUUID(),
            payload.getClass().getSimpleName(),
            Instant.now(),
            "auth-service",
            payload
        );
    }
}

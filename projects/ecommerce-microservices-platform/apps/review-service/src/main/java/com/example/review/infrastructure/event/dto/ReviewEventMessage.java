package com.example.review.infrastructure.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Infrastructure DTO for Kafka event serialization.
 * Maps domain ReviewEvent fields to contract-defined JSON field names.
 * Domain layer must NOT depend on this class.
 */
public record ReviewEventMessage(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") Instant occurredAt,
        String source,
        Object payload
) {
}

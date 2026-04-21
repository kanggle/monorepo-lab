package com.example.order.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring user-service UserWithdrawn contract.
 * See specs/contracts/events/user-events.md
 */
public record UserWithdrawnEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        UserWithdrawnPayload payload
) {
    public record UserWithdrawnPayload(
            String userId,
            String withdrawnAt
    ) {}
}

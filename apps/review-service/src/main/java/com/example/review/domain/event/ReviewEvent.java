package com.example.review.domain.event;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record ReviewEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String source,
        ReviewEventPayload payload
) {
    public static ReviewEvent created(ReviewCreatedPayload payload, Clock clock) {
        return of("ReviewCreated", payload, clock);
    }

    public static ReviewEvent updated(ReviewUpdatedPayload payload, Clock clock) {
        return of("ReviewUpdated", payload, clock);
    }

    public static ReviewEvent deleted(ReviewDeletedPayload payload, Clock clock) {
        return of("ReviewDeleted", payload, clock);
    }

    private static ReviewEvent of(String eventType, ReviewEventPayload payload, Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        return new ReviewEvent(UUID.randomUUID(), eventType, Instant.now(clock), "review-service", payload);
    }
}

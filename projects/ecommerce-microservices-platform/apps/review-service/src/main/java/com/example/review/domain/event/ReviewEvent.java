package com.example.review.domain.event;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event envelope for review lifecycle events.
 *
 * <p>TASK-BE-403 (ADR-MONO-030 Step 4 facet c): {@code tenantId} added as a
 * top-level envelope field alongside the existing standard fields. Consumers
 * that do not yet read {@code tenant_id} are unaffected (additive change).
 */
public record ReviewEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String source,
        String tenantId,
        ReviewEventPayload payload
) {
    public static ReviewEvent created(ReviewCreatedPayload payload, String tenantId, Clock clock) {
        return of("ReviewCreated", payload, tenantId, clock);
    }

    public static ReviewEvent updated(ReviewUpdatedPayload payload, String tenantId, Clock clock) {
        return of("ReviewUpdated", payload, tenantId, clock);
    }

    public static ReviewEvent deleted(ReviewDeletedPayload payload, String tenantId, Clock clock) {
        return of("ReviewDeleted", payload, tenantId, clock);
    }

    private static ReviewEvent of(String eventType, ReviewEventPayload payload, String tenantId, Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        return new ReviewEvent(UUID.randomUUID(), eventType, Instant.now(clock), "review-service", tenantId, payload);
    }
}

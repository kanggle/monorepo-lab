package com.example.shipping.infrastructure.event;

import java.util.UUID;

/**
 * Small UUID-parse helper for the shipping-service consumers migrated to
 * {@link com.example.messaging.dedupe.EventDedupePort} (ADR-MONO-058 D7, TASK-BE-569).
 * Mirrors {@code ReservationUuids} in product-service's TASK-BE-428 adoption.
 */
final class EventIds {

    private EventIds() {
    }

    /**
     * Parses a UUID, returning {@code null} for null/blank input — dedupe is then skipped
     * (the port runs the work unconditionally, mirroring the pre-D7
     * {@code EventDeduplicationChecker} behaviour). A non-blank malformed value throws
     * {@link IllegalArgumentException} → the consumer's error handler routes it to the DLQ
     * (non-retryable).
     */
    static UUID parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }
}

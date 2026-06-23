package com.example.product.infrastructure.event;

import java.util.UUID;

/** Small UUID-parse helper for the reservation saga consumers (TASK-BE-428). */
final class ReservationUuids {

    private ReservationUuids() {
    }

    /**
     * Parses a UUID, returning {@code null} for null/blank input. A non-blank malformed value
     * throws {@link IllegalArgumentException} → the consumer's error handler routes it to the
     * DLQ (non-retryable).
     */
    static UUID parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }
}

package com.example.order.infrastructure.event;

import java.time.Instant;
import java.util.UUID;

class EventFieldParser {

    private EventFieldParser() {
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static Instant parseInstant(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required but was null or blank");
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse " + fieldName + ": " + value, e);
        }
    }

    /**
     * Parses an envelope {@code event_id} for {@link com.example.messaging.dedupe.EventDedupePort},
     * returning {@code null} for null/blank input (dedupe is then skipped — the port runs the
     * work unconditionally, mirroring the pre-ADR-MONO-058-D7 {@code EventDeduplicationChecker}
     * behaviour). A non-blank malformed value throws {@link IllegalArgumentException} — routed
     * to the DLQ (non-retryable), same convention as {@code ReservationUuids.parseOrNull} in
     * product-service's TASK-BE-428 adoption.
     */
    static UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }
}

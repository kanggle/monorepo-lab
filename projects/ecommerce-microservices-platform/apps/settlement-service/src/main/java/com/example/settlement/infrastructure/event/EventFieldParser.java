package com.example.settlement.infrastructure.event;

import java.time.Instant;
import java.util.UUID;

final class EventFieldParser {

    private EventFieldParser() {
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Parses an ISO-8601 instant; null/blank/unparseable falls back to {@code now}. */
    static Instant parseInstantOrNow(String value) {
        if (isBlank(value)) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    /**
     * Parses an envelope {@code event_id} for {@link com.example.messaging.dedupe.EventDedupePort}
     * (ADR-MONO-058 D7), returning {@code null} for null/blank input — dedupe is then skipped
     * (the port runs the work unconditionally, mirroring the pre-D7 {@code ProcessedEventStore}
     * behaviour, which likewise treated a blank id as "never a duplicate"). A non-blank
     * malformed value throws {@link IllegalArgumentException} — routed to the DLQ (non-retryable).
     */
    static UUID parseUuidOrNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return UUID.fromString(value);
    }
}

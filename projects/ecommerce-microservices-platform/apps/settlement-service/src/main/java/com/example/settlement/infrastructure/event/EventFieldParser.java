package com.example.settlement.infrastructure.event;

import java.time.Instant;

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
}

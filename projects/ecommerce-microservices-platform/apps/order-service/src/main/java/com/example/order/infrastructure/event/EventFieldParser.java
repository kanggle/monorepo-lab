package com.example.order.infrastructure.event;

import java.time.Instant;

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
}

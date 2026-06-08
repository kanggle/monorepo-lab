package com.example.product.infrastructure.reconciliation;

import java.util.UUID;

/** Small shared helpers for the wms reconciliation consumers. */
final class Reconciliations {

    private Reconciliations() {
    }

    /**
     * Parses a UUID, returning {@code null} for null/blank input. A non-blank malformed
     * value throws {@link IllegalArgumentException} → the consumer's error handler routes
     * it to the DLQ (non-retryable).
     */
    static UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }
}

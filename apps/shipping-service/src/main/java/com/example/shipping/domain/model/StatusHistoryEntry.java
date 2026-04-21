package com.example.shipping.domain.model;

import java.time.Instant;

public record StatusHistoryEntry(
        ShippingStatus status,
        Instant changedAt
) {
    public StatusHistoryEntry {
        if (status == null) {
            throw new IllegalArgumentException("Status must not be null");
        }
        if (changedAt == null) {
            throw new IllegalArgumentException("ChangedAt must not be null");
        }
    }
}

package com.example.erp.notification.domain.recipient;

import java.util.Objects;

/**
 * Employee-id VO — the resolved recipient of a notification (an opaque
 * {@code emp-...} master id from the approval payload; no display-name
 * resolution in v1, § Scope discipline).
 */
public record Recipient(String employeeId) {

    public Recipient {
        Objects.requireNonNull(employeeId, "employeeId");
        if (employeeId.isBlank()) {
            throw new IllegalArgumentException("employeeId must not be blank");
        }
    }
}

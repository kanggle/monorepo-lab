package com.example.erp.readmodel.domain.common;

/**
 * Lifecycle status of a projected master row, mirroring the producer's master
 * status (erp E2). {@code RETIRED} is a logical status — the projection row is
 * retained (never deleted), so an {@code ?asOf} read before the retirement
 * still resolves it.
 *
 * <p>This is NOT a local state machine (T4 N/A): it merely reflects the value
 * carried by the consumed {@code *.changed} event's {@code after.status}.
 */
public enum MasterStatus {
    ACTIVE,
    RETIRED;

    /**
     * Parses a master status from the event payload, defaulting to
     * {@link #ACTIVE} when absent/blank (a CREATED/UPDATED event without an
     * explicit status is active). A {@code RETIRED} change-kind overrides this
     * at the application layer regardless of the payload.
     */
    public static MasterStatus fromOrActive(String raw) {
        if (raw == null || raw.isBlank()) {
            return ACTIVE;
        }
        return RETIRED.name().equalsIgnoreCase(raw.trim()) ? RETIRED : ACTIVE;
    }
}

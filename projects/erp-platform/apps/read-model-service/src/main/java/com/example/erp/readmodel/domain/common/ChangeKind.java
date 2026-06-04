package com.example.erp.readmodel.domain.common;

/**
 * Master-change discriminator carried by every consumed {@code *.changed.v1}
 * event payload (erp-masterdata-events.md § Payload schemas). The read-model
 * applies each kind to its projection table:
 *
 * <ul>
 *   <li>{@link #CREATED} / {@link #UPDATED} / {@link #PARENT_MOVED} → upsert the
 *       latest {@code after} values.</li>
 *   <li>{@link #RETIRED} → mark {@code status = RETIRED} + set {@code effectiveTo},
 *       retaining the row (never a delete — erp E2).</li>
 * </ul>
 *
 * {@link #PARENT_MOVED} is emitted only for {@code department}.
 */
public enum ChangeKind {
    CREATED,
    UPDATED,
    RETIRED,
    PARENT_MOVED;

    /**
     * Parses a change-kind from the event payload. An unknown / null value is a
     * malformed payload — callers treat it as invalid (route to DLT), so this
     * returns {@code null} rather than guessing.
     */
    public static ChangeKind fromOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ChangeKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** True when this kind retires the aggregate (logical retire, not delete). */
    public boolean isRetire() {
        return this == RETIRED;
    }
}

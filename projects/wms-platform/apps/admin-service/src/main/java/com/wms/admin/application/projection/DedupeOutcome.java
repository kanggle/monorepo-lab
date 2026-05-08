package com.wms.admin.application.projection;

/**
 * Outcome enum recorded on each {@code admin_event_dedupe} row.
 *
 * <ul>
 *   <li>{@link #APPLIED} — fresh insert, projection mutation applied.</li>
 *   <li>{@link #DUPLICATE} — eventId already present (Kafka redelivery).</li>
 *   <li>{@link #IGNORED_DUPLICATE_LATE} — eventId fresh, but the target
 *       row's {@code last_event_at} is newer than the event's
 *       {@code occurredAt}; the dedupe row is updated to this outcome and
 *       the mutation is skipped (LWW guard, idempotency.md § 2.3).</li>
 *   <li>{@link #FAILED} — projection threw and the TX rolled back; this
 *       outcome value exists for future "skip-on-permanent-failure" wiring
 *       but is not written by v1 code (rollback removes the dedupe row, so
 *       the next delivery retries fresh).</li>
 * </ul>
 */
public enum DedupeOutcome {
    APPLIED,
    DUPLICATE,
    IGNORED_DUPLICATE_LATE,
    FAILED
}

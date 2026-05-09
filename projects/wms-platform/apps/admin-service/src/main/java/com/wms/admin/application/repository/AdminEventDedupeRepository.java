package com.wms.admin.application.repository;

import com.wms.admin.application.projection.DedupeOutcome;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Application-layer repository for the {@code admin_event_dedupe} table.
 *
 * <p>Per {@code idempotency.md § 2.3}, projection consumers must run their
 * mutation in the same {@code @Transactional} boundary as a dedupe insert. This
 * repository factors out the insert-then-flush + LWW-late update pattern so the
 * 4 projection services share a single implementation.
 */
public interface AdminEventDedupeRepository {

    /**
     * Attempt to record this {@code eventId} as APPLIED.
     *
     * @return {@link DedupeOutcome#APPLIED} if a fresh row was inserted —
     *         the caller proceeds with the projection mutation.
     *         {@link DedupeOutcome#DUPLICATE} if a row already exists —
     *         the caller skips the mutation entirely (Kafka redelivery).
     */
    DedupeOutcome tryRecord(UUID eventId, String eventType);

    /**
     * Update an existing dedupe row's outcome. Used after a fresh
     * {@link #tryRecord} returns APPLIED but the read-model row's
     * {@code last_event_at} is newer than the event's {@code occurredAt}
     * (out-of-order arrival — see {@code idempotency.md § 2.3 step 2a-else}).
     */
    void markStale(UUID eventId);

    /** Lifetime aggregate counts. Powers the {@code /operations/projection-status} endpoint. */
    LifetimeCounts countLifetime();

    /**
     * Returns {@code MAX(processed_at)} per {@code eventType} for the supplied
     * event types. Consumers compose this with the topic ↔ eventType-prefix map
     * (see {@code KafkaLagProbe}) to derive each topic's
     * {@code lastProjectedAt} value.
     */
    Map<String, Instant> maxProcessedAtByEventType(Collection<String> eventTypes);

    record LifetimeCounts(long applied, long ignoredDuplicate, long ignoredDuplicateLate, long failed) {

        public static LifetimeCounts zero() {
            return new LifetimeCounts(0, 0, 0, 0);
        }
    }
}

package com.example.messaging.dedupe;

import java.util.UUID;

/**
 * Outbound port for consumer-side eventId dedupe.
 *
 * <p>Implementations apply the supplied {@link Runnable} exactly once per
 * {@code eventId} by inserting a row into a per-service dedupe table inside the
 * caller's transaction. Duplicate inserts are detected via primary-key violation
 * and reported as {@link Outcome#IGNORED_DUPLICATE} without re-running the work.
 *
 * <p>The interface lives in {@code libs/java-messaging} so every service shares
 * the same idempotent-consumer contract; the persistence implementation lives in
 * each service's adapter layer because the dedupe table's retention policy and
 * tenant scoping is service-specific.
 *
 * <p>Authoritative reference: {@code rules/traits/transactional.md} §T8.
 */
public interface EventDedupePort {

    /**
     * Apply the supplied work exactly once per {@code eventId}.
     *
     * <p>Behaviour:
     * <ul>
     *   <li>If {@code eventId} has not been processed → insert dedupe row,
     *       run {@code work}, return {@link Outcome#APPLIED}.</li>
     *   <li>If {@code eventId} already exists → return
     *       {@link Outcome#IGNORED_DUPLICATE} and skip {@code work}.</li>
     *   <li>If {@code work} throws → bubble the exception up; the surrounding
     *       transaction rolls back, including the dedupe row.</li>
     * </ul>
     *
     * @param eventId   inbound event identifier (UUIDv7 from the envelope)
     * @param eventType inbound event type for observability
     * @param work      side-effecting body to run on first occurrence
     * @return outcome — {@link Outcome#APPLIED} or {@link Outcome#IGNORED_DUPLICATE};
     *         {@link Outcome#FAILED} is reserved for implementations that catch
     *         {@code work}'s exception (most do not)
     */
    Outcome process(UUID eventId, String eventType, Runnable work);

    enum Outcome {
        APPLIED,
        IGNORED_DUPLICATE,
        FAILED
    }
}

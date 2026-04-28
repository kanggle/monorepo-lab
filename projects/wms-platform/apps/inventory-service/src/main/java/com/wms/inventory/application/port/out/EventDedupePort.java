package com.wms.inventory.application.port.out;

import java.util.UUID;

/**
 * Out-port for consumer-side eventId dedupe (trait T8).
 *
 * <p>Implementations must be transactional: the dedupe row is inserted in the
 * same DB transaction as the use-case's other writes. A duplicate eventId is
 * detected by primary-key violation and reported as
 * {@link Outcome#IGNORED_DUPLICATE} without re-executing the supplied work.
 *
 * <p>Authoritative reference:
 * {@code rules/traits/transactional.md} (T8),
 * {@code specs/services/inventory-service/idempotency.md} §2.
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
     * @param eventId   inbound event identifier (UUIDv7 from envelope)
     * @param eventType inbound event type for observability
     * @param work      side-effecting body to run on first occurrence
     * @return outcome — {@link Outcome#APPLIED} or {@link Outcome#IGNORED_DUPLICATE}
     */
    Outcome process(UUID eventId, String eventType, Runnable work);

    enum Outcome {
        APPLIED,
        IGNORED_DUPLICATE,
        FAILED
    }
}

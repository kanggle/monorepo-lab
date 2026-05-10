package com.wms.outbound.application.port.out;

import java.util.UUID;

/**
 * Out-port used by the saga sweeper (TASK-BE-050) to clone an existing
 * {@code outbound_outbox} row into a fresh PENDING row with a new
 * {@code eventId}, preserving the original payload (and its embedded
 * downstream-meaningful fields such as {@code reservationId}, {@code lines})
 * and partition key. The cloned row's JSON envelope {@code eventId} field
 * is rewritten to match the new row id so consumer-side eventId dedupe
 * sees a fresh value.
 *
 * <p>The sweeper does NOT reconstruct the domain event from saga state —
 * the original outbox row is the canonical record of what was sent
 * (lines, locations, quantities), which the saga itself does not
 * fully retain. Cloning preserves correctness without adding a
 * cross-aggregate join.
 *
 * <p>If no original outbox row matches {@code (aggregateId, eventType)}
 * the sweeper logs a WARN and skips the saga — there is nothing to
 * re-emit. This should not happen in practice (saga + outbox row are
 * co-committed) but is defensive against a partial migration / manual
 * DB intervention.
 *
 * <p>Implementations declare {@code @Transactional(propagation = MANDATORY)}
 * so the cloned row joins the caller's transaction (T3 outbox pattern).
 */
public interface OutboxReEmitterPort {

    /**
     * @return {@code true} when an original row was found and cloned;
     *         {@code false} when no matching row exists (defensive — sweeper logs and skips).
     */
    boolean reEmit(UUID aggregateId, String eventType);
}

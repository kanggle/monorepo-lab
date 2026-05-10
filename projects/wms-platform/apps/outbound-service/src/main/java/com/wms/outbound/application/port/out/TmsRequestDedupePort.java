package com.wms.outbound.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Out-port for the {@code tms_request_dedupe} table — local fallback for
 * vendor idempotency (integration-heavy I4).
 *
 * <p>Lifecycle (see {@code external-integrations.md} §2.7):
 * <ol>
 *   <li>Adapter looks up {@link #findSnapshot(UUID)} for {@code shipment.id}
 *       BEFORE making a network call.</li>
 *   <li>If a snapshot is present, the adapter returns the cached
 *       {@link TmsAcknowledgement} without an HTTP roundtrip.</li>
 *   <li>If absent, the adapter calls TMS; on a 2xx response it persists
 *       the snapshot via {@link #saveSnapshot(UUID, java.time.Instant, String)}.</li>
 * </ol>
 *
 * <p>The persistence happens in a fresh {@code REQUIRES_NEW} transaction
 * because the saga TX has already committed when the adapter runs (TMS
 * call is in a post-commit listener — no shared TX, no rollback).
 */
public interface TmsRequestDedupePort {

    /**
     * Returns the cached response snapshot for the given request id, or
     * {@link Optional#empty()} if this is the first send.
     */
    Optional<String> findSnapshot(UUID requestId);

    /**
     * Persists the response snapshot for the given request id. Idempotent
     * by PK — concurrent inserts will lose to the first writer; callers
     * tolerate this and fall back to {@link #findSnapshot(UUID)} on
     * conflict.
     *
     * @param requestId        equals {@code Shipment.id}
     * @param sentAt           wall-clock at successful TMS ack
     * @param responseSnapshot JSON-serialised vendor acknowledgement
     */
    void saveSnapshot(UUID requestId, java.time.Instant sentAt, String responseSnapshot);
}

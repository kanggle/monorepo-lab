package com.wms.inventory.application.port.out;

import com.wms.inventory.application.query.ReservationListCriteria;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.domain.model.Reservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link Reservation} aggregates.
 *
 * <p>The application service loads-then-saves; the adapter implements
 * optimistic locking via the {@code version} column. Reservation creation
 * collides on the {@code picking_request_id} unique constraint to surface
 * domain-level idempotency on top of the Redis store (per
 * {@code idempotency.md} §1.7).
 */
public interface ReservationRepository {

    Optional<Reservation> findById(UUID id);

    Optional<Reservation> findByPickingRequestId(UUID pickingRequestId);

    /**
     * Insert a brand-new reservation. Surfaces a
     * {@link com.wms.inventory.domain.exception.DuplicateRequestException}
     * when {@code picking_request_id} collides with an existing row.
     */
    Reservation insert(Reservation reservation);

    /**
     * Version-checked UPDATE on the aggregate. Optimistic-lock conflict
     * surfaces via {@code OptimisticLockingFailureException}.
     */
    Reservation updateWithVersionCheck(Reservation reservation);

    Optional<ReservationView> findViewById(UUID id);

    PageView<ReservationView> listViews(ReservationListCriteria criteria);

    /**
     * Streaming-friendly fetch of {@code RESERVED} rows whose {@code expiresAt}
     * is at or before {@code asOf}. Used by the TTL expiry job; the page size
     * keeps each batch tractable.
     */
    List<Reservation> findExpired(Instant asOf, int limit);

    /** Live count of {@code RESERVED} rows for the active-reservation gauge. */
    long countActive();
}

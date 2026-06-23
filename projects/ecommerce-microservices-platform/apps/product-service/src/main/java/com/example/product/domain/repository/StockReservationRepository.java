package com.example.product.domain.repository;

import com.example.product.domain.model.reservation.StockReservation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the {@link StockReservation} aggregate (TASK-BE-428).
 */
public interface StockReservationRepository {

    StockReservation save(StockReservation reservation);

    Optional<StockReservation> findByOrderId(String orderId);

    /**
     * BACKORDERED reservations whose lines include {@code variantId}, ordered by
     * {@code createdAt} ASC (FIFO). Used by the restock retry leg to re-attempt the oldest
     * waiting orders first when a short variant is replenished.
     */
    List<StockReservation> findBackorderedHoldingVariant(UUID variantId);
}

package com.example.settlement.domain.repository;

import com.example.settlement.domain.model.OrderSnapshot;

import java.util.Optional;

/**
 * Persistence port for the {@code OrderPlaced} line snapshot cache. Implemented in
 * the infrastructure layer; the domain/application depend only on this interface.
 */
public interface OrderSnapshotRepository {

    /** Upsert idempotently on {@code orderId} — a re-delivered OrderPlaced is a no-op. */
    void upsert(OrderSnapshot snapshot);

    /** Joins the snapshot by {@code orderId} (settlement's only tenant/seller source). */
    Optional<OrderSnapshot> findByOrderId(String orderId);
}

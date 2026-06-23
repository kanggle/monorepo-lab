package com.example.product.infrastructure.persistence;

import com.example.product.domain.model.reservation.ReservationStatus;
import com.example.product.infrastructure.persistence.entity.StockReservationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StockReservationJpaRepository extends JpaRepository<StockReservationJpaEntity, UUID> {

    Optional<StockReservationJpaEntity> findByOrderId(String orderId);

    /**
     * BACKORDERED reservations that have a line for {@code variantId}, oldest first (FIFO).
     * {@code DISTINCT} guards against a reservation appearing twice if it (illegally) held the
     * variant on multiple lines.
     */
    @Query("""
            SELECT DISTINCT r FROM StockReservationJpaEntity r
            JOIN r.lines l
            WHERE r.status = :status AND l.variantId = :variantId
            ORDER BY r.createdAt ASC
            """)
    List<StockReservationJpaEntity> findHoldingVariantByStatusOrderByCreatedAt(
            @Param("status") ReservationStatus status, @Param("variantId") UUID variantId);
}

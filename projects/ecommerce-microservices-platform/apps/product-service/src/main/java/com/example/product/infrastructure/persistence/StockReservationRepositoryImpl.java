package com.example.product.infrastructure.persistence;

import com.example.product.domain.model.reservation.ReservationStatus;
import com.example.product.domain.model.reservation.StockReservation;
import com.example.product.domain.repository.StockReservationRepository;
import com.example.product.infrastructure.persistence.entity.StockReservationJpaEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class StockReservationRepositoryImpl implements StockReservationRepository {

    private final StockReservationJpaRepository jpaRepository;

    StockReservationRepositoryImpl(StockReservationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public StockReservation save(StockReservation reservation) {
        // Re-apply mutable state onto the existing managed row (preserving @Version) when the
        // aggregate is an update; otherwise persist the new aggregate.
        StockReservationJpaEntity entity = jpaRepository.findById(reservation.getId())
                .map(existing -> {
                    existing.syncFrom(reservation);
                    return existing;
                })
                .orElseGet(() -> StockReservationJpaEntity.from(reservation));
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<StockReservation> findByOrderId(String orderId) {
        return jpaRepository.findByOrderId(orderId).map(StockReservationJpaEntity::toDomain);
    }

    @Override
    public List<StockReservation> findBackorderedHoldingVariant(UUID variantId) {
        return jpaRepository
                .findHoldingVariantByStatusOrderByCreatedAt(ReservationStatus.BACKORDERED, variantId)
                .stream()
                .map(StockReservationJpaEntity::toDomain)
                .toList();
    }
}

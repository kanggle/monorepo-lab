package com.example.product.infrastructure.persistence;

import com.example.product.domain.model.StockAdjustmentRequest;
import com.example.product.domain.repository.StockAdjustmentRequestRepository;
import com.example.product.infrastructure.persistence.entity.StockAdjustmentRequestJpaEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class StockAdjustmentRequestRepositoryImpl implements StockAdjustmentRequestRepository {

    private final StockAdjustmentRequestJpaRepository jpaRepository;

    StockAdjustmentRequestRepositoryImpl(StockAdjustmentRequestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<StockAdjustmentRequest> find(UUID variantId, String idempotencyKey) {
        return jpaRepository.findByVariantIdAndIdempotencyKey(variantId, idempotencyKey)
                .map(StockAdjustmentRequestJpaEntity::toDomain);
    }

    @Override
    public StockAdjustmentRequest insert(StockAdjustmentRequest request) {
        // saveAndFlush, not save: the INSERT must reach Postgres inside the caller's
        // try-block so a UNIQUE (variant_id, idempotency_key) violation arrives as a
        // DataIntegrityViolationException it can translate to 409, rather than being
        // deferred to commit-time flush (past the catch, past the inventory write).
        return jpaRepository.saveAndFlush(StockAdjustmentRequestJpaEntity.fromDomain(request))
                .toDomain();
    }
}

package com.example.product.infrastructure.persistence;

import com.example.product.infrastructure.persistence.entity.StockAdjustmentRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@code stock_adjustment_request} (TASK-BE-536). */
interface StockAdjustmentRequestJpaRepository extends JpaRepository<StockAdjustmentRequestJpaEntity, Long> {

    Optional<StockAdjustmentRequestJpaEntity> findByVariantIdAndIdempotencyKey(
            UUID variantId, String idempotencyKey);
}

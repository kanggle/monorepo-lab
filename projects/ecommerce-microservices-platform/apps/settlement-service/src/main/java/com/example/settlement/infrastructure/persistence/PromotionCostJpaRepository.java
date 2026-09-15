package com.example.settlement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionCostJpaRepository extends JpaRepository<PromotionCostJpaEntity, String> {

    /** All rows of an order (tenant-agnostic — addressed by globally-unique orderId, the consume path). */
    List<PromotionCostJpaEntity> findByOrderId(String orderId);
}

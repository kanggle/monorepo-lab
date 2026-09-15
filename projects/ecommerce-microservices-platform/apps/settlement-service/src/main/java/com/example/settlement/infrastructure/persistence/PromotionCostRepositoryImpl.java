package com.example.settlement.infrastructure.persistence;

import com.example.settlement.domain.model.PromotionCost;
import com.example.settlement.domain.repository.PromotionCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Promotion-cost ledger persistence adapter (TASK-BE-592). Insert-only (F3). */
@Repository
@RequiredArgsConstructor
public class PromotionCostRepositoryImpl implements PromotionCostRepository {

    private final PromotionCostJpaRepository jpaRepository;

    @Override
    public void appendAll(List<PromotionCost> costs) {
        jpaRepository.saveAll(costs.stream().map(PromotionCostRepositoryImpl::toEntity).toList());
    }

    @Override
    public List<PromotionCost> findByOrderId(String orderId) {
        return jpaRepository.findByOrderId(orderId).stream()
                .map(PromotionCostRepositoryImpl::toDomain)
                .toList();
    }

    private static PromotionCostJpaEntity toEntity(PromotionCost c) {
        return PromotionCostJpaEntity.of(c.costId(), c.tenantId(), c.orderId(), c.paymentId(), c.couponId(),
                c.type(), c.amountMinor(), c.reversesCostId(), c.occurredAt());
    }

    private static PromotionCost toDomain(PromotionCostJpaEntity e) {
        return new PromotionCost(e.getCostId(), e.getTenantId(), e.getOrderId(), e.getPaymentId(),
                e.getCouponId(), e.getType(), e.getAmountMinor(), e.getReversesCostId(), e.getOccurredAt());
    }
}

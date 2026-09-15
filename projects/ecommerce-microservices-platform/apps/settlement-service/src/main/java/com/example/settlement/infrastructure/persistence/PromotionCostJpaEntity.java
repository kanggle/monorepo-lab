package com.example.settlement.infrastructure.persistence;

import com.example.settlement.domain.model.PromotionCostType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One append-only promotion-cost row (TASK-BE-592). Immutable after insert (F3) — there is no
 * update method; a correction is a separate REVERSAL row.
 */
@Entity
@Table(name = "promotion_cost")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionCostJpaEntity {

    @Id
    @Column(name = "cost_id", nullable = false)
    private String costId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "coupon_id")
    private String couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private PromotionCostType type;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "reverses_cost_id")
    private String reversesCostId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    static PromotionCostJpaEntity of(String costId, String tenantId, String orderId, String paymentId,
                                     String couponId, PromotionCostType type, long amountMinor,
                                     String reversesCostId, Instant occurredAt) {
        PromotionCostJpaEntity e = new PromotionCostJpaEntity();
        e.costId = costId;
        e.tenantId = tenantId;
        e.orderId = orderId;
        e.paymentId = paymentId;
        e.couponId = couponId;
        e.type = type;
        e.amountMinor = amountMinor;
        e.reversesCostId = reversesCostId;
        e.occurredAt = occurredAt;
        return e;
    }
}

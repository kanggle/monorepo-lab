package com.example.settlement.infrastructure.persistence;

import com.example.settlement.domain.model.AccrualType;
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
 * One append-only commission-ledger row. Immutable after insert (F3) — there is no
 * update method; a correction is a separate REVERSAL row. {@code tenant_id} comes
 * from the snapshot (AC-7); {@code seller_id} from the snapshot line (Step 3).
 */
@Entity
@Table(name = "commission_accrual")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionAccrualJpaEntity {

    @Id
    @Column(name = "accrual_id", nullable = false)
    private String accrualId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "seller_id", nullable = false)
    private String sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private AccrualType type;

    @Column(name = "gross_minor", nullable = false)
    private long grossMinor;

    @Column(name = "rate_bps", nullable = false)
    private int rateBps;

    @Column(name = "commission_minor", nullable = false)
    private long commissionMinor;

    @Column(name = "seller_net_minor", nullable = false)
    private long sellerNetMinor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** For REVERSAL rows, the parent ACCRUAL's id (per-accrual cumulative reversal). Null on ACCRUAL rows. */
    @Column(name = "reverses_accrual_id")
    private String reversesAccrualId;

    static CommissionAccrualJpaEntity of(String accrualId, String tenantId, String orderId,
                                         String paymentId, String sellerId, AccrualType type,
                                         long grossMinor, int rateBps, long commissionMinor,
                                         long sellerNetMinor, Instant occurredAt,
                                         String reversesAccrualId) {
        CommissionAccrualJpaEntity e = new CommissionAccrualJpaEntity();
        e.accrualId = accrualId;
        e.tenantId = tenantId;
        e.orderId = orderId;
        e.paymentId = paymentId;
        e.sellerId = sellerId;
        e.type = type;
        e.grossMinor = grossMinor;
        e.rateBps = rateBps;
        e.commissionMinor = commissionMinor;
        e.sellerNetMinor = sellerNetMinor;
        e.occurredAt = occurredAt;
        e.reversesAccrualId = reversesAccrualId;
        return e;
    }
}

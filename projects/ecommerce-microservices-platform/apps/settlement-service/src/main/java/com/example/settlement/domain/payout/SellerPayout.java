package com.example.settlement.domain.payout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * Seller-payout aggregate root (architecture.md § Period close + simulated payout).
 * One row per {@code (period_id, seller_id)} (UNIQUE), folded at close from the
 * seller's in-window accruals:
 * <pre>
 *   payable_net_minor = Σ seller_net_minor (ACCRUAL − REVERSAL)
 *   commission_minor  = Σ commission_minor
 *   accrual_count     = number of accrual rows folded
 * </pre>
 * Created {@code PENDING} at close; {@code payout_reference} / {@code paid_at} are
 * NULL while PENDING.
 *
 * <p><b>State machine.</b> {@code PENDING → PAID | FAILED} via {@link #markPaid} /
 * {@link #markFailed}. The execution transition is a <b>separate</b> operator step
 * (TASK-BE-416 — the {@code SellerPayoutPort} simulated adapter is NOT wired in this
 * increment); these transition methods are present for that follow-up but are not
 * invoked by the close path.
 */
@Entity
@Table(name = "seller_payout")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerPayout {

    @Id
    @Column(name = "payout_id", length = 255, nullable = false)
    private String payoutId;

    @Column(name = "period_id", length = 255, nullable = false)
    private String periodId;

    @Column(name = "tenant_id", length = 255, nullable = false)
    private String tenantId;

    @Column(name = "seller_id", length = 255, nullable = false)
    private String sellerId;

    @Column(name = "payable_net_minor", nullable = false)
    private long payableNetMinor;

    @Column(name = "commission_minor", nullable = false)
    private long commissionMinor;

    @Column(name = "accrual_count", nullable = false)
    private int accrualCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PayoutStatus status;

    @Column(name = "payout_reference", length = 255)
    private String payoutReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private SellerPayout(String payoutId, String periodId, String tenantId, String sellerId,
                         long payableNetMinor, long commissionMinor, int accrualCount,
                         PayoutStatus status) {
        this.payoutId = payoutId;
        this.periodId = periodId;
        this.tenantId = tenantId;
        this.sellerId = sellerId;
        this.payableNetMinor = payableNetMinor;
        this.commissionMinor = commissionMinor;
        this.accrualCount = accrualCount;
        this.status = status;
    }

    /**
     * Create a PENDING payout for a seller at period close. {@code payableNetMinor}
     * must be positive — net-zero sellers ({@code ≤ 0}) get no payout row (decision
     * 7); the close use-case applies that skip before calling this factory.
     */
    public static SellerPayout pending(String payoutId, String periodId, String tenantId,
                                       String sellerId, long payableNetMinor,
                                       long commissionMinor, int accrualCount) {
        Objects.requireNonNull(payoutId, "payoutId");
        Objects.requireNonNull(periodId, "periodId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sellerId, "sellerId");
        if (payableNetMinor <= 0) {
            throw new IllegalArgumentException(
                    "seller payout must have a positive payable_net_minor (net-zero sellers are"
                            + " skipped at close, decision 7): sellerId=" + sellerId
                            + " payableNetMinor=" + payableNetMinor);
        }
        return new SellerPayout(payoutId, periodId, tenantId, sellerId,
                payableNetMinor, commissionMinor, accrualCount, PayoutStatus.PENDING);
    }

    /**
     * Mark this payout PAID (PENDING→PAID), recording the disbursement reference +
     * timestamp. <b>TASK-BE-416 execution path — not invoked in this increment.</b>
     */
    public void markPaid(String payoutReference, Instant paidAt) {
        if (status != PayoutStatus.PENDING) {
            throw new IllegalStateException("seller payout not PENDING: " + payoutId);
        }
        Objects.requireNonNull(payoutReference, "payoutReference");
        Objects.requireNonNull(paidAt, "paidAt");
        this.status = PayoutStatus.PAID;
        this.payoutReference = payoutReference;
        this.paidAt = paidAt;
    }

    /**
     * Mark this payout FAILED (PENDING→FAILED). <b>TASK-BE-416 execution path — not
     * invoked in this increment.</b>
     */
    public void markFailed() {
        if (status != PayoutStatus.PENDING) {
            throw new IllegalStateException("seller payout not PENDING: " + payoutId);
        }
        this.status = PayoutStatus.FAILED;
    }
}

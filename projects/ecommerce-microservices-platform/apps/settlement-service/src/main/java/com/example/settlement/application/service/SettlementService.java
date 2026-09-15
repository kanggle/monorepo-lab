package com.example.settlement.application.service;

import com.example.settlement.application.exception.SnapshotNotFoundException;
import com.example.settlement.domain.model.CommissionAccrual;
import com.example.settlement.domain.model.CommissionPolicy;
import com.example.settlement.domain.model.CommissionRate;
import com.example.settlement.domain.model.CommissionSplit;
import com.example.settlement.domain.model.OrderSnapshot;
import com.example.settlement.domain.model.OrderSnapshotLine;
import com.example.settlement.domain.model.PromotionCost;
import com.example.settlement.domain.model.PromotionCostType;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
import com.example.settlement.domain.repository.OrderSnapshotRepository;
import com.example.settlement.domain.repository.PromotionCostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the three settlement use-cases consumed from the order/payment event
 * streams (the transaction boundary lives on the infrastructure consumers, which
 * also run the dedupe in the same {@code @Transactional}).
 *
 * <ul>
 *   <li><b>snapshot</b> ({@code OrderPlaced}) — cache the order's tenant + per-line
 *       seller/gross + coupon discount, idempotent on {@code orderId}.</li>
 *   <li><b>accrue</b> ({@code PaymentCompleted}) — join the snapshot, split each line
 *       by its seller's effective rate, append ACCRUAL rows; book the coupon discount as a
 *       separate promotion-cost row (TASK-BE-592). Idempotent on {@code (orderId, paymentId)};
 *       missing snapshot → {@link SnapshotNotFoundException} (F2).</li>
 *   <li><b>reverse</b> ({@code PaymentRefunded}) — proportionally claw back commission and
 *       promotion cost for a (partial or full) refund, appending REVERSAL rows linked to their
 *       parents. Idempotent on the envelope {@code event_id} (the consumer dedupe) — a payment
 *       may emit several partial refunds.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final OrderSnapshotRepository snapshotRepository;
    private final CommissionAccrualRepository accrualRepository;
    private final CommissionRateResolver rateResolver;
    private final PromotionCostRepository promotionCostRepository;

    /** Caches an OrderPlaced line snapshot (with its coupon discount), idempotent on {@code orderId}. */
    @Transactional
    public void recordSnapshot(RecordOrderSnapshotCommand cmd) {
        snapshotRepository.upsert(new OrderSnapshot(
                cmd.orderId(), cmd.tenantId(), cmd.lines(), cmd.discountMinor(), cmd.couponId()));
    }

    /**
     * Accrues commission for a captured payment. Joins the snapshot for the order's
     * tenant + per-line seller, computes {@code commission = round(gross × rate / 10000)}
     * and {@code seller_net = gross − commission} per line, appends an ACCRUAL row per
     * line. A replayed {@code (orderId, paymentId)} is a no-op (AC-6). No snapshot →
     * F2 (raise → retry → DLQ).
     *
     * <p>TASK-BE-592: commission stays on the pre-discount line gross; a coupon discount is
     * booked as one order-level promotion-cost row (the platform bears it).
     */
    @Transactional
    public void accrue(AccruePaymentCommand cmd) {
        if (accrualRepository.existsAccrualFor(cmd.orderId(), cmd.paymentId())) {
            log.debug("Accrual already booked for orderId={}, paymentId={} — skipping",
                    cmd.orderId(), cmd.paymentId());
            return;
        }

        OrderSnapshot snapshot = snapshotRepository.findByOrderId(cmd.orderId())
                .orElseThrow(() -> new SnapshotNotFoundException(cmd.orderId()));

        List<CommissionAccrual> rows = new ArrayList<>(snapshot.lines().size());
        for (OrderSnapshotLine line : snapshot.lines()) {
            CommissionRate rate = rateResolver.resolve(snapshot.tenantId(), line.sellerId());
            CommissionSplit split = CommissionPolicy.split(line.grossMinor(), rate);
            rows.add(CommissionAccrual.accrual(
                    snapshot.tenantId(), cmd.orderId(), cmd.paymentId(),
                    line.sellerId(), split, cmd.occurredAt()));
        }
        accrualRepository.appendAll(rows);
        log.info("Accrued {} commission line(s) for orderId={}, paymentId={}, tenant={}",
                rows.size(), cmd.orderId(), cmd.paymentId(), snapshot.tenantId());

        // The platform bears the coupon discount. It is booked as its own row so the commission
        // rows above — and every seller balance and payout fold — stay exactly as they would be
        // without the coupon. Same transaction, same (orderId, paymentId) guard.
        if (snapshot.discountMinor() > 0) {
            promotionCostRepository.appendAll(List.of(PromotionCost.cost(
                    snapshot.tenantId(), cmd.orderId(), cmd.paymentId(), snapshot.couponId(),
                    snapshot.discountMinor(), cmd.occurredAt())));
            log.info("Booked promotion cost {} for orderId={}, paymentId={}, couponId={}",
                    snapshot.discountMinor(), cmd.orderId(), cmd.paymentId(), snapshot.couponId());
        }
    }

    /**
     * Proportionally claws back an order's commission — and, for a coupon-discounted order, its
     * promotion cost — on a (partial or full) refund.
     *
     * <p>The refund fraction is taken against the order's <b>captured</b> amount,
     * {@code captured = Σ ACCRUAL.gross − snapshot.discountMinor} (TASK-BE-592): a refund gives
     * back captured money, and for a discounted order that is less than the accrued gross. For
     * each ACCRUAL row it reverses {@code round(orig_gross × refundAmount / captured)} of the
     * gross — re-split via {@link CommissionPolicy} and negated so every REVERSAL row
     * independently satisfies {@code commission + seller_net == gross} — clamped to the row's
     * remaining un-reversed gross (cumulative cap). The promotion cost reverses
     * {@code round(discount × refundAmount / captured)}, clamped the same way. On the final
     * refund ({@code cmd.fullyRefunded()}) both reverse the <b>exact remaining</b>, so the order
     * nets to exactly zero per seller and in promotion cost, absorbing any partial-rounding drift.
     *
     * <p>Without a coupon {@code captured == accruedGross} — the behaviour before TASK-BE-592.
     * Idempotency is the consumer's {@code event_id} dedupe — a payment may emit several partial
     * refunds, each a distinct event. No accruals (cancel-before-capture) → no-op.
     */
    @Transactional
    public void reverse(ReversePaymentCommand cmd) {
        List<CommissionAccrual> accruals = accrualRepository.findAccrualsByOrderId(cmd.orderId());
        if (accruals.isEmpty()) {
            log.info("No accruals to reverse for orderId={} (refundPaymentId={})",
                    cmd.orderId(), cmd.paymentId());
            return;
        }

        // Per-accrual cumulative already-reversed (positive magnitudes), keyed by parent accrualId.
        List<CommissionAccrual> existingReversals = accrualRepository.findReversalsByOrderId(cmd.orderId());
        // Legacy guard: a pre-BE-425 REVERSAL row has no reverses_accrual_id (it reversed the
        // whole order under the old full-only semantics). Such a row is unattributable to a single
        // accrual, so per-accrual already-reversed cannot be computed — and the order was already
        // fully reversed. Treating it as 0 would let a (hypothetical) new refund reverse the order a
        // SECOND time. This path is shielded upstream (the payment is REFUNDED → the domain rejects
        // any further refund; a Kafka replay carries the same event_id → deduped), but guard
        // explicitly: any legacy null-parent reversal ⇒ the order is already settled, no-op.
        if (existingReversals.stream().anyMatch(r -> r.reversesAccrualId() == null)) {
            log.info("Order orderId={} has legacy full-reversal rows (no parent link) — already "
                    + "reversed; skipping proportional clawback (refundPaymentId={})",
                    cmd.orderId(), cmd.paymentId());
            return;
        }
        Map<String, long[]> reversedByAccrual = new HashMap<>(); // [gross, commission, sellerNet]
        for (CommissionAccrual r : existingReversals) {
            long[] acc = reversedByAccrual.computeIfAbsent(r.reversesAccrualId(), k -> new long[3]);
            acc[0] += -r.grossMinor();        // reversal amounts are negative — negate to magnitude
            acc[1] += -r.commissionMinor();
            acc[2] += -r.sellerNetMinor();
        }

        long accruedGross = accruals.stream().mapToLong(CommissionAccrual::grossMinor).sum();
        if (accruedGross <= 0) {
            log.info("Order accrued gross is non-positive for orderId={} — nothing to reverse", cmd.orderId());
            return;
        }

        // TASK-BE-592: the refund fraction's denominator is the CAPTURED amount. The snapshot is
        // always present here (accrual required it); if it is somehow gone the order is treated as
        // having had no coupon, which is exactly the pre-TASK-BE-592 arithmetic.
        long discountMinor = snapshotRepository.findByOrderId(cmd.orderId())
                .map(OrderSnapshot::discountMinor)
                .orElse(0L);
        long capturedMinor = accruedGross - discountMinor;
        if (capturedMinor <= 0) {
            log.warn("Captured amount is non-positive for orderId={} (accruedGross={}, discount={}) — "
                    + "cannot apportion the refund; skipping", cmd.orderId(), accruedGross, discountMinor);
            return;
        }

        List<CommissionAccrual> reversals = new ArrayList<>(accruals.size());
        for (CommissionAccrual a : accruals) {
            long[] already = reversedByAccrual.getOrDefault(a.accrualId(), new long[3]);
            long remainingGross = a.grossMinor() - already[0];
            if (remainingGross <= 0) {
                continue; // this accrual is already fully reversed
            }

            CommissionSplit reverseSplit;
            if (cmd.fullyRefunded()) {
                // Reverse the exact remaining per field → per-row (and per-seller) exact zero.
                long remCommission = a.commissionMinor() - already[1];
                long remSellerNet = a.sellerNetMinor() - already[2];
                reverseSplit = new CommissionSplit(-remainingGross, a.rateBps(), -remCommission, -remSellerNet);
            } else {
                long portion = Math.min(proportional(a.grossMinor(), cmd.refundAmount(), capturedMinor),
                        remainingGross); // cumulative cap
                if (portion <= 0) {
                    continue;
                }
                reverseSplit = CommissionPolicy.split(portion, a.rateBps()).negated();
            }
            reversals.add(a.toReversal(cmd.paymentId(), cmd.occurredAt(), reverseSplit));
        }

        List<PromotionCost> costReversals = discountMinor > 0
                ? promotionCostReversals(cmd, capturedMinor)
                : List.of();

        if (reversals.isEmpty() && costReversals.isEmpty()) {
            log.info("Proportional reversal produced no rows for orderId={} (refundAmount={}, fully={})",
                    cmd.orderId(), cmd.refundAmount(), cmd.fullyRefunded());
            return;
        }
        if (!reversals.isEmpty()) {
            accrualRepository.appendAll(reversals);
        }
        if (!costReversals.isEmpty()) {
            promotionCostRepository.appendAll(costReversals);
        }
        log.info("Reversed {} accrual line(s) and {} promotion-cost row(s) for orderId={}, refundPaymentId={} "
                        + "(refundAmount={}, captured={}, fully={})",
                reversals.size(), costReversals.size(), cmd.orderId(), cmd.paymentId(),
                cmd.refundAmount(), capturedMinor, cmd.fullyRefunded());
    }

    /**
     * The promotion-cost share of a refund: per COST row, {@code round(amount × refund / captured)}
     * clamped to its remaining un-reversed amount, or the exact remaining on the final refund.
     */
    private List<PromotionCost> promotionCostReversals(ReversePaymentCommand cmd, long capturedMinor) {
        List<PromotionCost> rows = promotionCostRepository.findByOrderId(cmd.orderId());
        Map<String, Long> reversedByCost = new HashMap<>();
        for (PromotionCost r : rows) {
            if (r.type() == PromotionCostType.REVERSAL) {
                reversedByCost.merge(r.reversesCostId(), -r.amountMinor(), Long::sum);
            }
        }
        List<PromotionCost> out = new ArrayList<>();
        for (PromotionCost c : rows) {
            if (c.type() != PromotionCostType.COST) {
                continue;
            }
            long remaining = c.amountMinor() - reversedByCost.getOrDefault(c.costId(), 0L);
            if (remaining <= 0) {
                continue; // already fully reversed
            }
            long portion = cmd.fullyRefunded()
                    ? remaining
                    : Math.min(proportional(c.amountMinor(), cmd.refundAmount(), capturedMinor), remaining);
            if (portion > 0) {
                out.add(c.toReversal(cmd.paymentId(), cmd.occurredAt(), portion));
            }
        }
        return out;
    }

    /**
     * {@code round(amountMinor × refundAmount / baseMinor)} (HALF_UP) via BigDecimal — the same
     * rounding vehicle as {@link CommissionPolicy} — to avoid {@code long} overflow on the
     * intermediate product and keep money exact.
     */
    private static long proportional(long amountMinor, long refundAmount, long baseMinor) {
        return BigDecimal.valueOf(amountMinor)
                .multiply(BigDecimal.valueOf(refundAmount))
                .divide(BigDecimal.valueOf(baseMinor), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}

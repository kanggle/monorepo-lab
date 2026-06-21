package com.example.settlement.application.service;

import com.example.settlement.application.exception.SnapshotNotFoundException;
import com.example.settlement.domain.model.CommissionAccrual;
import com.example.settlement.domain.model.CommissionPolicy;
import com.example.settlement.domain.model.CommissionRate;
import com.example.settlement.domain.model.CommissionSplit;
import com.example.settlement.domain.model.OrderSnapshot;
import com.example.settlement.domain.model.OrderSnapshotLine;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
import com.example.settlement.domain.repository.OrderSnapshotRepository;
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
 *       seller/gross, idempotent on {@code orderId}.</li>
 *   <li><b>accrue</b> ({@code PaymentCompleted}) — join the snapshot, split each line
 *       by its seller's effective rate, append ACCRUAL rows. Idempotent on
 *       {@code (orderId, paymentId)}; missing snapshot → {@link SnapshotNotFoundException}
 *       (F2).</li>
 *   <li><b>reverse</b> ({@code PaymentRefunded}) — negate the order's accruals to
 *       net-zero, append REVERSAL rows. Idempotent on {@code (orderId, refundPaymentId)}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final OrderSnapshotRepository snapshotRepository;
    private final CommissionAccrualRepository accrualRepository;
    private final CommissionRateResolver rateResolver;

    /** Caches an OrderPlaced line snapshot, idempotent on {@code orderId}. */
    @Transactional
    public void recordSnapshot(RecordOrderSnapshotCommand cmd) {
        snapshotRepository.upsert(new OrderSnapshot(cmd.orderId(), cmd.tenantId(), cmd.lines()));
    }

    /**
     * Accrues commission for a captured payment. Joins the snapshot for the order's
     * tenant + per-line seller, computes {@code commission = round(gross × rate / 10000)}
     * and {@code seller_net = gross − commission} per line, appends an ACCRUAL row per
     * line. A replayed {@code (orderId, paymentId)} is a no-op (AC-6). No snapshot →
     * F2 (raise → retry → DLQ).
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
    }

    /**
     * Proportionally claws back an order's commission on a (partial or full) refund.
     * For each ACCRUAL row, reverses {@code round(orig_gross × refundAmount / accruedGross)}
     * of the gross — re-split via {@link CommissionPolicy} and negated so every REVERSAL
     * row independently satisfies {@code commission + seller_net == gross} — clamped to the
     * row's remaining un-reversed gross (cumulative cap). On the final refund
     * ({@code cmd.fullyRefunded()}) it reverses the <b>exact remaining</b> per field so the
     * order nets to exactly zero per seller, absorbing any partial-rounding drift.
     *
     * <p>Idempotency is the consumer's {@code event_id} dedupe — a payment may emit several
     * partial refunds, each a distinct event. No accruals (cancel-before-capture) → no-op.
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
        Map<String, long[]> reversedByAccrual = new HashMap<>(); // [gross, commission, sellerNet]
        for (CommissionAccrual r : accrualRepository.findReversalsByOrderId(cmd.orderId())) {
            String parent = r.reversesAccrualId();
            if (parent == null) {
                continue; // legacy full-reversal row without a parent link — not attributable
            }
            long[] acc = reversedByAccrual.computeIfAbsent(parent, k -> new long[3]);
            acc[0] += -r.grossMinor();        // reversal amounts are negative — negate to magnitude
            acc[1] += -r.commissionMinor();
            acc[2] += -r.sellerNetMinor();
        }

        long accruedGross = accruals.stream().mapToLong(CommissionAccrual::grossMinor).sum();
        if (accruedGross <= 0) {
            log.info("Order accrued gross is non-positive for orderId={} — nothing to reverse", cmd.orderId());
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
                long portion = Math.min(proportionalGross(a.grossMinor(), cmd.refundAmount(), accruedGross),
                        remainingGross); // cumulative cap
                if (portion <= 0) {
                    continue;
                }
                reverseSplit = CommissionPolicy.split(portion, a.rateBps()).negated();
            }
            reversals.add(a.toReversal(cmd.paymentId(), cmd.occurredAt(), reverseSplit));
        }

        if (reversals.isEmpty()) {
            log.info("Proportional reversal produced no rows for orderId={} (refundAmount={}, fully={})",
                    cmd.orderId(), cmd.refundAmount(), cmd.fullyRefunded());
            return;
        }
        accrualRepository.appendAll(reversals);
        log.info("Reversed {} accrual line(s) for orderId={}, refundPaymentId={} (refundAmount={}, fully={})",
                reversals.size(), cmd.orderId(), cmd.paymentId(), cmd.refundAmount(), cmd.fullyRefunded());
    }

    /**
     * {@code round(grossMinor × refundAmount / accruedGross)} (HALF_UP) via BigDecimal — the
     * same rounding vehicle as {@link CommissionPolicy} — to avoid {@code long} overflow on the
     * intermediate product and keep money exact.
     */
    private static long proportionalGross(long grossMinor, long refundAmount, long accruedGross) {
        return BigDecimal.valueOf(grossMinor)
                .multiply(BigDecimal.valueOf(refundAmount))
                .divide(BigDecimal.valueOf(accruedGross), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}

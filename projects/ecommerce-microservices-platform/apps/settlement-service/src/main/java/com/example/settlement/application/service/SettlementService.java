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

import java.util.ArrayList;
import java.util.List;

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
     * Reverses an order's accruals on refund (v1 = full). Loads the order's ACCRUAL
     * rows and appends a negating REVERSAL per row → seller balance + platform
     * commission return to their pre-order values (AC-5). Idempotent on
     * {@code (orderId, refundPaymentId)} (AC-6). No accruals (cancel-before-capture,
     * or already reversed) → no-op.
     */
    @Transactional
    public void reverse(ReversePaymentCommand cmd) {
        if (accrualRepository.existsReversalFor(cmd.orderId(), cmd.paymentId())) {
            log.debug("Reversal already booked for orderId={}, refundPaymentId={} — skipping",
                    cmd.orderId(), cmd.paymentId());
            return;
        }

        List<CommissionAccrual> accruals = accrualRepository.findAccrualsByOrderId(cmd.orderId());
        if (accruals.isEmpty()) {
            log.info("No accruals to reverse for orderId={} (refundPaymentId={})",
                    cmd.orderId(), cmd.paymentId());
            return;
        }

        List<CommissionAccrual> reversals = accruals.stream()
                .map(a -> a.toReversal(cmd.paymentId(), cmd.occurredAt()))
                .toList();
        accrualRepository.appendAll(reversals);
        log.info("Reversed {} accrual line(s) for orderId={}, refundPaymentId={}",
                reversals.size(), cmd.orderId(), cmd.paymentId());
    }
}

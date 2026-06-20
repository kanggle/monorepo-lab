package com.example.settlement.application.service;

import com.example.settlement.application.event.SettlementPeriodClosedEvent;
import com.example.settlement.application.port.SettlementEventPublisher;
import com.example.settlement.application.port.SettlementMetricsPort;
import com.example.settlement.application.view.PayoutView;
import com.example.settlement.application.view.PeriodView;
import com.example.settlement.domain.payout.SellerPayout;
import com.example.settlement.domain.period.PeriodNotFoundException;
import com.example.settlement.domain.period.SettlementPeriod;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
import com.example.settlement.domain.repository.SellerAccrualFold;
import com.example.settlement.domain.repository.SellerPayoutRepository;
import com.example.settlement.domain.repository.SettlementPeriodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Closes a settlement period (architecture.md § Period close + simulated payout).
 * Mirrors the finance-platform ledger {@code CloseAccountingPeriodUseCase} "one
 * transaction: load → aggregate → close → publish" structure, but the output is
 * {@code seller_payout} rows (PENDING) rather than a balance snapshot.
 *
 * <p>The single {@code @Transactional} boundary (AC-5) does, atomically:
 * <ol>
 *   <li><b>load</b> the period in the caller's tenant — absent / cross-tenant →
 *       {@link PeriodNotFoundException} (404, M3);</li>
 *   <li><b>aggregate</b> the EXISTING in-window accruals per seller (read-only fold
 *       over {@code [from, to)} — accruals are NEVER mutated, F3);</li>
 *   <li><b>create payouts</b> — one PENDING {@link SellerPayout} per seller with a
 *       <b>positive</b> {@code payableNetMinor} (net-zero sellers skipped, decision 7,
 *       AC-4);</li>
 *   <li><b>close</b> the period OPEN→CLOSED with {@code sellerCount = #payouts}
 *       (re-close → {@code PERIOD_ALREADY_CLOSED}, 409);</li>
 *   <li><b>append</b> {@code settlement.period.closed.v1} to the outbox (co-committed
 *       — AC-6).</li>
 * </ol>
 * If any step throws, the whole transaction rolls back: the period stays OPEN, no
 * payout rows, no outbox row (AC-5). The metric is recorded after the publish.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloseSettlementPeriodUseCase {

    private final SettlementPeriodRepository periodRepository;
    private final SellerPayoutRepository payoutRepository;
    private final CommissionAccrualRepository accrualRepository;
    private final SettlementEventPublisher eventPublisher;
    private final SettlementMetricsPort metrics;
    private final Clock clock;

    @Transactional
    public PeriodView close(String periodId, String tenantId, String closedBy) {
        SettlementPeriod period = periodRepository.findById(periodId, tenantId)
                .orElseThrow(() -> new PeriodNotFoundException(
                        "settlement period not found: " + periodId));

        // Read-only fold over the immutable accrual ledger (F3) — half-open window.
        List<SellerAccrualFold> folds =
                accrualRepository.foldByPeriod(tenantId, period.from(), period.to());

        // Net-zero skip (decision 7): only sellers with a positive payable get a row.
        List<SellerPayout> payouts = new ArrayList<>(folds.size());
        for (SellerAccrualFold fold : folds) {
            if (fold.payableNetMinor() <= 0) {
                continue;
            }
            payouts.add(SellerPayout.pending(
                    UUID.randomUUID().toString(), periodId, tenantId, fold.sellerId(),
                    fold.payableNetMinor(), fold.commissionMinor(), fold.accrualCount()));
        }

        Instant closedAt = clock.instant();
        // close() throws PeriodAlreadyClosedException (409) if not OPEN.
        period.close(closedAt, closedBy, payouts.size());

        periodRepository.save(period);
        List<SellerPayout> savedPayouts = payoutRepository.saveAll(payouts);

        // Append settlement.period.closed.v1 in THIS transaction (transactional outbox).
        eventPublisher.publishPeriodClosed(toEvent(period, closedAt, savedPayouts));

        metrics.recordPeriodClosed();
        log.info("Closed settlement period periodId={} tenant={} sellerCount={}",
                periodId, tenantId, savedPayouts.size());

        return PeriodView.detail(period, savedPayouts.stream().map(PayoutView::from).toList());
    }

    private SettlementPeriodClosedEvent toEvent(SettlementPeriod period, Instant closedAt,
                                                List<SellerPayout> payouts) {
        List<SettlementPeriodClosedEvent.PayoutLine> lines = payouts.stream()
                .map(p -> new SettlementPeriodClosedEvent.PayoutLine(
                        p.sellerId(), p.payableNetMinor(), p.commissionMinor(), p.accrualCount()))
                .toList();
        SettlementPeriodClosedEvent.Payload payload = new SettlementPeriodClosedEvent.Payload(
                period.periodId(), period.tenantId(),
                period.from().toString(), period.to().toString(), closedAt.toString(),
                payouts.size(), lines);
        return SettlementPeriodClosedEvent.of(
                UUID.randomUUID().toString(), period.tenantId(), closedAt, payload);
    }
}

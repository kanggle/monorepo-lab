package com.example.payment.application.service;

import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.application.port.out.StrandedRefundRepository;
import com.example.payment.domain.model.StrandedRefund;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Stranded-refund auto-reconciliation sweeper (TASK-BE-438, ADR-MONO-005 § 2.3 D3 Category A).
 *
 * <p>Polls open ({@code STRANDED}) stranded-refund obligations whose {@code next_attempt_at} is due
 * and dispatches each to {@link StrandedRefundReconciler} for PG-state-first reconciliation. The
 * per-record work runs in a {@code REQUIRES_NEW} boundary on the <b>separate</b> reconciler bean
 * (AOP proxy), so one poisoned record cannot roll back the batch (AC-6 / F5).
 *
 * <p><b>Concurrency exclusivity.</b> {@code @Scheduled(fixedDelay)} runs on a single scheduler
 * thread and never overlaps a previous still-running invocation of the same method, so two ticks
 * cannot reconcile the same record concurrently (Edge Case "Concurrent sweeper ticks"). No
 * pessimistic row lock is needed; the reconciler additionally re-checks {@code isOpen()} under a
 * fresh load before acting (idempotent if a record advanced between selection and reconciliation).
 *
 * <p>Disabled in {@code standalone} (no DB / no PG) and when
 * {@code payment.stranded-refund.enabled=false}.
 */
@Slf4j
@Component
@Profile("!standalone")
@ConditionalOnProperty(name = "payment.stranded-refund.enabled", havingValue = "true", matchIfMissing = true)
public class StrandedRefundSweeper {

    private final StrandedRefundRepository repository;
    private final StrandedRefundReconciler reconciler;
    private final Clock clock;
    private final int batchSize;

    public StrandedRefundSweeper(StrandedRefundRepository repository,
                                 StrandedRefundReconciler reconciler,
                                 PaymentMetricRecorder metrics,
                                 Clock clock,
                                 @Value("${payment.stranded-refund.batch-size:50}") int batchSize) {
        this.repository = repository;
        this.reconciler = reconciler;
        this.clock = clock;
        this.batchSize = Math.max(1, batchSize);
        // Register the open-obligations gauge once, backed by the repository count (TASK-BE-438 SLO).
        metrics.registerStrandedOpenGauge(repository::countOpen);
    }

    @Scheduled(fixedDelayString = "${payment.stranded-refund.fixed-delay-ms:60000}",
            initialDelayString = "${payment.stranded-refund.initial-delay-ms:30000}")
    public void sweep() {
        Instant now = Instant.now(clock);
        List<StrandedRefund> due;
        try {
            due = repository.findDue(now, batchSize);
        } catch (Exception e) {
            log.error("stranded_refund_sweeper_findDue_failed reason={}", e.toString(), e);
            return;
        }
        if (due.isEmpty()) {
            return;
        }
        log.info("stranded_refund_sweeper_batch count={} now={}", due.size(), now);
        for (StrandedRefund record : due) {
            try {
                reconciler.reconcile(record.getId());
            } catch (Exception e) {
                // Per-record failure isolated (the reconciler's REQUIRES_NEW TX rolled back).
                // The next due tick retries; the batch continues.
                log.warn("stranded_refund_sweeper_record_failed id={} reason={}",
                        record.getId(), e.toString());
            }
        }
    }

    /** Visible for tests so a single tick can be driven on demand. */
    public void sweepOnce() {
        sweep();
    }
}

package com.example.shipping.application.service;

import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.repository.ShippingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Unattended auto-collect tracking sweep (TASK-BE-360 / ADR-007 D5-3). Periodically polls
 * in-flight shipments ({@code SHIPPED}/{@code IN_TRANSIT} that carry a tracking number +
 * carrier) and advances each forward to its latest carrier-reported status — the same
 * forward-advance the admin pull uses ({@link CarrierAdvanceProcessor}). It is the
 * <b>backstop</b> that converges a shipment to DELIVERED even when the operator never presses
 * refresh and the aggregator webhook (TASK-BE-294) is lost / delayed.
 *
 * <p><b>Net-zero by default.</b> The scheduler bean that drives this is gated OFF
 * (TASK-BE-360 AC-3); with {@code mode=mock} + blank {@code mock-status} even an enabled sweep
 * is a real no-op (the carrier port returns empty). <b>Best-effort per item</b> (AC-4): one
 * shipment's failure / carrier outage / unmapped status must never abort the batch — each item
 * is advanced in its own transaction ({@link CarrierAdvanceProcessor#advanceFromCarrier} is
 * {@code REQUIRES_NEW}) and any exception is caught + counted, leaving the other items
 * unaffected. <b>Forward-only</b> — never regresses a shipment (reuses the domain's
 * unidirectional transition via {@link ShippingForwardAdvancer}).
 *
 * <p><b>Lock bypass for tests (ShedLock trap).</b> {@link #sweep()} is the directly-callable
 * use-case; tests assert business behaviour by calling it (NOT by waiting on the
 * {@code @Scheduled} tick), because {@code @SchedulerLock(lockAtLeastFor=...)} on the scheduler
 * bean would make a test run only the first invocation and silently no-op the rest.
 */
@Slf4j
@Service
public class AutoCollectTrackingService {

    /** Micrometer counter for sweep outcomes, tagged by {@code outcome}. */
    static final String SWEEP_COUNTER = "carrier_auto_collect_processed";

    private final ShippingRepository shippingRepository;
    private final CarrierAdvanceProcessor carrierAdvanceProcessor;
    private final MeterRegistry meterRegistry;
    private final int batchSize;

    public AutoCollectTrackingService(
            ShippingRepository shippingRepository,
            CarrierAdvanceProcessor carrierAdvanceProcessor,
            MeterRegistry meterRegistry,
            @Value("${shipping.carrier.auto-collect.batch-size:100}") int batchSize) {
        this.shippingRepository = shippingRepository;
        this.carrierAdvanceProcessor = carrierAdvanceProcessor;
        this.meterRegistry = meterRegistry;
        this.batchSize = batchSize;
    }

    /** Per-tick counts (immutable result of one {@link #sweep()} run). */
    public record SweepResult(int processed, int advanced, int noOp, int failed) {
    }

    /** Run one sweep over the configured batch size. Directly callable (bypasses ShedLock). */
    public SweepResult sweep() {
        return sweep(batchSize);
    }

    /**
     * Process one bounded batch of in-flight shipments: load up to {@code limit} oldest
     * in-flight shipments with tracking, then advance each best-effort (per-item exception
     * isolated). Returns per-tick counts.
     */
    public SweepResult sweep(int limit) {
        List<Shipping> batch = shippingRepository.findInFlightWithTracking(limit);
        if (batch.isEmpty()) {
            log.debug("Auto-collect sweep: no in-flight shipments with tracking; clean no-op");
            return new SweepResult(0, 0, 0, 0);
        }

        int advanced = 0;
        int noOp = 0;
        int failed = 0;
        for (Shipping shipping : batch) {
            try {
                CarrierAdvanceProcessor.Outcome outcome =
                        carrierAdvanceProcessor.advanceFromCarrier(shipping, "auto-collect");
                if (outcome == CarrierAdvanceProcessor.Outcome.ADVANCED) {
                    advanced++;
                } else {
                    noOp++;
                }
            } catch (Exception e) {
                // F3 / AC-4: one shipment's failure must NOT abort the batch — isolate + count.
                failed++;
                meterRegistry.counter(SWEEP_COUNTER, "outcome", "failed").increment();
                log.warn("Auto-collect sweep: shipping {} failed; continuing batch: {}",
                        shipping.getShippingId(), e.getMessage(), e);
            }
        }
        meterRegistry.counter(SWEEP_COUNTER, "outcome", "advanced").increment(advanced);
        meterRegistry.counter(SWEEP_COUNTER, "outcome", "no_op").increment(noOp);

        SweepResult result = new SweepResult(batch.size(), advanced, noOp, failed);
        log.info("Auto-collect sweep tick: processed={} advanced={} noOp={} failed={}",
                result.processed(), result.advanced(), result.noOp(), result.failed());
        return result;
    }
}

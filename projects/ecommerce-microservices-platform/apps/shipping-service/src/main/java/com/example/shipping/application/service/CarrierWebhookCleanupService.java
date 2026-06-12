package com.example.shipping.application.service;

import com.example.shipping.infrastructure.webhook.ProcessedCarrierWebhookJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Retention / cleanup sweep for the BE-294 {@code processed_carrier_webhooks} dedup table
 * (TASK-BE-361 / ADR-007 D5-4). Every inbound aggregator webhook persists its
 * {@code delivery_id} as a permanent idempotency marker, so the table grows unbounded; this
 * sweep periodically deletes markers OLDER than a retention window, in bounded batches.
 *
 * <p><b>AC-2 (idempotency preserved).</b> The cutoff is
 * {@code clock.instant().minus(retentionDays, DAYS)} and the repo delete predicate is a
 * STRICT {@code received_at < cutoff} — so every marker INSIDE the retention window is
 * retained. A carrier re-delivery of the same {@code delivery_id} within that window therefore
 * still hits an existing marker and stays a DUPLICATE no-op (BE-294 {@code registerIfFirst} is
 * unchanged). {@code retention-days} must stay safely longer than the aggregator's maximum
 * webhook re-delivery delay (F1).
 *
 * <p><b>AC-3 (scope).</b> Only {@code processed_carrier_webhooks} is touched here — the Kafka
 * event dedup table {@code processed_events} has its own independent retention
 * ({@code ProcessedEventCleanupScheduler}) and is never referenced by this sweep.
 *
 * <p><b>F2 (per-batch tx, bounded tick).</b> This service is intentionally NOT
 * {@code @Transactional}: each {@link ProcessedCarrierWebhookJpaRepository#deleteBatchOlderThan}
 * call commits its own bounded batch, so the loop never opens one giant runaway transaction. A
 * single tick is additionally bounded by {@code max-batches-per-tick} — a huge backlog drains
 * progressively across ticks rather than in one unbounded burst.
 *
 * <p><b>ShedLock test-bypass trap.</b> {@link #sweep()} is the directly-callable use-case; unit
 * tests assert behaviour by calling it (NOT by waiting on the scheduler tick), because the
 * scheduler bean's {@code @SchedulerLock(lockAtLeastFor=...)} would let a test run only the
 * first invocation and silently no-op the rest.
 */
@Slf4j
@Service
public class CarrierWebhookCleanupService {

    /** Micrometer counter for total markers deleted across all sweeps. */
    static final String DELETED_COUNTER = "carrier_webhook_dedup_cleanup_deleted";

    private final ProcessedCarrierWebhookJpaRepository repository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final int retentionDays;
    private final int batchSize;
    private final int maxBatchesPerTick;

    public CarrierWebhookCleanupService(
            ProcessedCarrierWebhookJpaRepository repository,
            MeterRegistry meterRegistry,
            Clock clock,
            @Value("${shipping.carrier.webhook.cleanup.retention-days:30}") int retentionDays,
            @Value("${shipping.carrier.webhook.cleanup.batch-size:500}") int batchSize,
            @Value("${shipping.carrier.webhook.cleanup.max-batches-per-tick:100}") int maxBatchesPerTick) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
        this.maxBatchesPerTick = maxBatchesPerTick;
    }

    /** Per-tick counts (immutable result of one {@link #sweep()} run). */
    public record CleanupResult(int deleted, int batches) {
    }

    /**
     * Run one bounded cleanup tick: delete dedup markers strictly older than
     * {@code now - retentionDays}, in batches of {@code batchSize}, stopping when a batch drains
     * (deletes {@code < batchSize}) OR {@code maxBatchesPerTick} is reached. Directly callable
     * (bypasses ShedLock). An empty / 0-eligible table is an immediate clean no-op.
     */
    public CleanupResult sweep() {
        Instant cutoff = clock.instant().minus(retentionDays, ChronoUnit.DAYS);

        int totalDeleted = 0;
        int batches = 0;
        while (batches < maxBatchesPerTick) {
            int deleted = repository.deleteBatchOlderThan(cutoff, batchSize);
            if (deleted == 0) {
                break;
            }
            totalDeleted += deleted;
            batches++;
            if (deleted < batchSize) {
                // Drained: fewer than a full batch eligible remain.
                break;
            }
        }

        if (totalDeleted == 0) {
            log.debug("Carrier-webhook dedup cleanup: no markers older than {} (retention={}d); "
                    + "clean no-op", cutoff, retentionDays);
            return new CleanupResult(0, 0);
        }

        meterRegistry.counter(DELETED_COUNTER).increment(totalDeleted);
        CleanupResult result = new CleanupResult(totalDeleted, batches);
        log.info("Carrier-webhook dedup cleanup tick: deleted={} batches={} cutoff={} retention={}d",
                result.deleted(), result.batches(), cutoff, retentionDays);
        return result;
    }
}

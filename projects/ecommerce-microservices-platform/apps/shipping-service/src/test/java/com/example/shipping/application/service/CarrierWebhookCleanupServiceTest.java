package com.example.shipping.application.service;

import com.example.shipping.application.service.CarrierWebhookCleanupService.CleanupResult;
import com.example.shipping.infrastructure.webhook.ProcessedCarrierWebhookJpaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the carrier-webhook dedup retention/cleanup sweep (TASK-BE-361). Every test
 * calls {@link CarrierWebhookCleanupService#sweep()} <b>directly</b> (bypassing the scheduler
 * bean's ShedLock — the documented ShedLock trap), asserting the batched-delete / drain-stop /
 * bounded-tick / cutoff-boundary business logic on a mocked repository.
 *
 * <p>AC-2 retention semantics: the service computes a STRICT {@code now - retentionDays} cutoff
 * and the repo predicate is {@code received_at < cutoff} — a marker with {@code received_at ==
 * cutoff} (or newer = in-window) is never selected, so it is retained and a carrier re-delivery
 * of that {@code delivery_id} stays a DUPLICATE no-op. These unit tests assert the cutoff value
 * the service computes (the query predicate itself, exercised by the repo, guarantees retention).
 */
@ExtendWith(MockitoExtension.class)
class CarrierWebhookCleanupServiceTest {

    private static final int RETENTION_DAYS = 30;
    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES = 100;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC);

    @Mock ProcessedCarrierWebhookJpaRepository repository;

    private SimpleMeterRegistry meterRegistry;
    private CarrierWebhookCleanupService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new CarrierWebhookCleanupService(
                repository, meterRegistry, clock, RETENTION_DAYS, BATCH_SIZE, MAX_BATCHES);
    }

    private Instant expectedCutoff() {
        return clock.instant().minus(RETENTION_DAYS, ChronoUnit.DAYS);
    }

    @Test
    void emptyTable_isCleanNoOp() {
        when(repository.deleteBatchOlderThan(eq(expectedCutoff()), anyInt())).thenReturn(0);

        CleanupResult result = service.sweep();

        assertThat(result).isEqualTo(new CleanupResult(0, 0));
        // single probe call, then stop
        verify(repository, times(1)).deleteBatchOlderThan(eq(expectedCutoff()), eq(BATCH_SIZE));
        // counter not incremented on a clean no-op
        assertThat(meterRegistry.find("carrier_webhook_dedup_cleanup_deleted").counter()).isNull();
    }

    @Test
    void deletesOlderThanRetention_inBatches_untilDrained() {
        // full batch, full batch, then a partial (remainder < batchSize) → loop ends
        int remainder = 137;
        when(repository.deleteBatchOlderThan(eq(expectedCutoff()), eq(BATCH_SIZE)))
                .thenReturn(BATCH_SIZE, BATCH_SIZE, remainder);

        CleanupResult result = service.sweep();

        assertThat(result.deleted()).isEqualTo(BATCH_SIZE + BATCH_SIZE + remainder);
        assertThat(result.batches()).isEqualTo(3);
        // exactly 3 calls — the partial-batch return ended the loop (drained)
        verify(repository, times(3)).deleteBatchOlderThan(eq(expectedCutoff()), eq(BATCH_SIZE));

        // AC-2 boundary: prove the cutoff is strictly now - retentionDays
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repository, times(3)).deleteBatchOlderThan(cutoffCaptor.capture(), anyInt());
        assertThat(cutoffCaptor.getAllValues())
                .allSatisfy(c -> assertThat(c).isEqualTo(expectedCutoff()));
    }

    @Test
    void boundedByMaxBatchesPerTick() {
        // never drains: every batch returns a full batchSize → the loop must stop at maxBatches
        when(repository.deleteBatchOlderThan(eq(expectedCutoff()), eq(BATCH_SIZE)))
                .thenReturn(BATCH_SIZE);

        CleanupResult result = service.sweep();

        assertThat(result.batches()).isEqualTo(MAX_BATCHES);
        assertThat(result.deleted()).isEqualTo(MAX_BATCHES * BATCH_SIZE);
        // no runaway tick: exactly maxBatches calls
        verify(repository, times(MAX_BATCHES)).deleteBatchOlderThan(eq(expectedCutoff()), eq(BATCH_SIZE));
    }

    @Test
    void counterIncrementedByDeletedCount() {
        when(repository.deleteBatchOlderThan(eq(expectedCutoff()), eq(BATCH_SIZE)))
                .thenReturn(BATCH_SIZE, 42);

        CleanupResult result = service.sweep();

        int expectedDeleted = BATCH_SIZE + 42;
        assertThat(result.deleted()).isEqualTo(expectedDeleted);
        assertThat(meterRegistry.get("carrier_webhook_dedup_cleanup_deleted").counter().count())
                .isEqualTo((double) expectedDeleted);
    }
}

package com.wms.inventory.adapter.in.web.filter;

import com.example.web.idempotency.IdempotencyMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer-backed {@link IdempotencyMetrics} for {@code inventory-service}
 * (ADR-MONO-038 I5), covering the observable signals named in
 * specs/services/inventory-service/idempotency.md §5:
 *
 * <ul>
 *   <li>{@code inventory.idempotency.lookup.count{result=hit|miss|conflict}} — counter</li>
 *   <li>{@code inventory.idempotency.lookup.duration{result}} — timer (p50/p95/p99)</li>
 *   <li>{@code inventory.idempotency.store.failure} — counter (fail-open store errors)</li>
 * </ul>
 *
 * <p>The body-mismatch counter {@code inventory.idempotency.mismatch.count} is
 * emitted by {@link InventoryIdempotencyErrorWriter#writeConflict}, not here, so
 * it is not conflated with the lock-held 503 that also tags {@code conflict}.
 */
public class InventoryIdempotencyMetrics implements IdempotencyMetrics {

    static final String METRIC_LOOKUP_COUNT = "inventory.idempotency.lookup.count";
    static final String METRIC_LOOKUP_DURATION = "inventory.idempotency.lookup.duration";
    static final String METRIC_STORE_FAILURE = "inventory.idempotency.store.failure";
    static final String TAG_RESULT = "result";

    private final MeterRegistry meterRegistry;

    public InventoryIdempotencyMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordLookup(String result, long durationNanos) {
        meterRegistry.counter(METRIC_LOOKUP_COUNT, TAG_RESULT, result).increment();
        Timer.builder(METRIC_LOOKUP_DURATION)
                .tag(TAG_RESULT, result)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordStoreFailure() {
        meterRegistry.counter(METRIC_STORE_FAILURE).increment();
    }
}

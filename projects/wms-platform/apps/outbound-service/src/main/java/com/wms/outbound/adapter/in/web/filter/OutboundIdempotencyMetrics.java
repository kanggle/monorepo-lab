package com.wms.outbound.adapter.in.web.filter;

import com.example.web.idempotency.IdempotencyMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer-backed {@link IdempotencyMetrics} for {@code outbound-service}
 * (ADR-MONO-038 I5), reproducing the metrics the former
 * {@code OutboundIdempotencyFilter} emitted:
 *
 * <ul>
 *   <li>{@code outbound.idempotency.lookup.count{result=hit|miss|conflict}} — counter</li>
 *   <li>{@code outbound.idempotency.lookup.duration{result}} — timer (p50/p95/p99)</li>
 *   <li>{@code outbound.idempotency.store.failure} — counter</li>
 * </ul>
 */
public class OutboundIdempotencyMetrics implements IdempotencyMetrics {

    static final String METRIC_LOOKUP_COUNT = "outbound.idempotency.lookup.count";
    static final String METRIC_LOOKUP_DURATION = "outbound.idempotency.lookup.duration";
    static final String METRIC_STORE_FAILURE = "outbound.idempotency.store.failure";
    static final String TAG_RESULT = "result";

    private final MeterRegistry meterRegistry;

    public OutboundIdempotencyMetrics(MeterRegistry meterRegistry) {
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

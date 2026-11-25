package com.wms.admin.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.ref.WeakReference;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TASK-BE-667 — {@code admin.query.cache.hit.rate} reads 0 (the TASK-BE-048 #8 design: v1 has no
 * query cache) and keeps reading 0 after a garbage collection. Registered as
 * {@code meterRegistry.gauge(name, 0.0d)} it held the boxed Double only weakly and read NaN after
 * the first GC.
 */
class ProjectionMetricsTest {

    @Test
    @DisplayName("admin.query.cache.hit.rate stays 0.0 after a GC actually ran (not NaN)")
    void cacheHitRateSurvivesGc() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new ProjectionMetrics(registry, Clock.systemUTC());

        Gauge gauge = registry.get(ProjectionMetrics.CACHE_HIT_RATE_METRIC).gauge();
        assertThat(gauge.value()).isEqualTo(0.0d);

        // Prove a collection happened: a weakly-held sentinel must be cleared. Without that the
        // assertion below would pass for the weak registration too and measure nothing.
        WeakReference<Object> sentinel = new WeakReference<>(new Object());
        for (int i = 0; i < 50 && sentinel.get() != null; i++) {
            System.gc();
            byte[] pressure = new byte[8 * 1024 * 1024];
            pressure[0] = 1;
        }
        assumeTrue(sentinel.get() == null, "no GC observed in this JVM — nothing to prove");

        assertThat(gauge.value()).as("value after GC").isEqualTo(0.0d);
    }
}

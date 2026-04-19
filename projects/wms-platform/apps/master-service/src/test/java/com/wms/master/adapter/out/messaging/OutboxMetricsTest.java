package com.wms.master.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/**
 * Pure-unit test for {@link OutboxMetrics}. Gauge depends on an EntityManager
 * which is null here — the test exercises only the counter paths, which is all
 * we need for the unit phase; gauge behaviour is verified by the integration
 * suite against a real database.
 */
class OutboxMetricsTest {

    @Test
    void recordPublishSuccess_incrementsSuccessCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        OutboxMetrics metrics = new OutboxMetrics(registry);

        metrics.recordPublishSuccess();
        metrics.recordPublishSuccess();

        assertThat(registry.counter(OutboxMetrics.PUBLISH_SUCCESS_TOTAL).count())
                .isEqualTo(2.0d);
    }

    @Test
    void recordPublishFailure_incrementsFailureCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        OutboxMetrics metrics = new OutboxMetrics(registry);

        metrics.recordPublishFailure();

        assertThat(registry.counter(OutboxMetrics.PUBLISH_FAILURE_TOTAL).count())
                .isEqualTo(1.0d);
    }

    @Test
    void allThreeMetersAreRegistered() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new OutboxMetrics(registry);

        assertThat(registry.find(OutboxMetrics.PUBLISH_SUCCESS_TOTAL).counter()).isNotNull();
        assertThat(registry.find(OutboxMetrics.PUBLISH_FAILURE_TOTAL).counter()).isNotNull();
        assertThat(registry.find(OutboxMetrics.PENDING_COUNT).gauge()).isNotNull();
    }
}

package com.wms.master.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.messaging.outbox.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class MasterOutboxPollingSchedulerTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxPublisher publisher = mock(OutboxPublisher.class);
    private final OutboxMetrics metrics = mock(OutboxMetrics.class);

    private final TestableScheduler scheduler = new TestableScheduler(publisher, kafka, metrics);

    @Test
    void resolvesTopic_forEachAggregate() {
        assertThat(scheduler.resolveTopic("master.warehouse.created")).isEqualTo("wms.master.warehouse.v1");
        assertThat(scheduler.resolveTopic("master.zone.created")).isEqualTo("wms.master.zone.v1");
        assertThat(scheduler.resolveTopic("master.zone.updated")).isEqualTo("wms.master.zone.v1");
        assertThat(scheduler.resolveTopic("master.zone.deactivated")).isEqualTo("wms.master.zone.v1");
        assertThat(scheduler.resolveTopic("master.zone.reactivated")).isEqualTo("wms.master.zone.v1");
        assertThat(scheduler.resolveTopic("master.location.created")).isEqualTo("wms.master.location.v1");
        assertThat(scheduler.resolveTopic("master.location.updated")).isEqualTo("wms.master.location.v1");
        assertThat(scheduler.resolveTopic("master.location.deactivated")).isEqualTo("wms.master.location.v1");
        assertThat(scheduler.resolveTopic("master.location.reactivated")).isEqualTo("wms.master.location.v1");
        assertThat(scheduler.resolveTopic("master.sku.created")).isEqualTo("wms.master.sku.v1");
        assertThat(scheduler.resolveTopic("master.sku.updated")).isEqualTo("wms.master.sku.v1");
        assertThat(scheduler.resolveTopic("master.sku.deactivated")).isEqualTo("wms.master.sku.v1");
        assertThat(scheduler.resolveTopic("master.sku.reactivated")).isEqualTo("wms.master.sku.v1");
        assertThat(scheduler.resolveTopic("master.partner.created")).isEqualTo("wms.master.partner.v1");
        assertThat(scheduler.resolveTopic("master.lot.expired")).isEqualTo("wms.master.lot.v1");
    }

    @Test
    void rejectsUnknownEventTypes() {
        assertThatThrownBy(() -> scheduler.resolveTopic(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scheduler.resolveTopic("inventory.stock.moved"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scheduler.resolveTopic("master"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Subclass exposing the protected {@code resolveTopic} hook for assertion.
     */
    private static final class TestableScheduler extends MasterOutboxPollingScheduler {
        private TestableScheduler(OutboxPublisher publisher,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  OutboxMetrics metrics) {
            super(publisher, kafkaTemplate, metrics);
        }

        @Override
        public String resolveTopic(String eventType) {
            return super.resolveTopic(eventType);
        }
    }
}

package com.wms.master.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wms.master.adapter.out.persistence.outbox.MasterOutboxEntity;
import com.wms.master.adapter.out.persistence.outbox.MasterOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit test for {@link MasterOutboxPublisher} (TASK-BE-438, outbox v2).
 *
 * <p>The generic poll/backoff/header mechanics live in (and are tested by)
 * {@code AbstractOutboxPublisher}. This test pins the master-service specifics:
 * the {@code master.<aggregate>.<action> → wms.master.<aggregate>.v1} topic
 * mapping (AC-1, including reject-unmapped), the v2 record headers + topic on a
 * real publish (AC-2), mark-published (AC-4), the {@code master.}-prefixed
 * metrics, and the preserved {@code master.outbox.pending.count} gauge.
 */
class MasterOutboxPublisherTest {

    private static final Instant OCCURRED = Instant.parse("2026-06-26T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-26T00:00:02Z"), ZoneOffset.UTC);

    // --- AC-1: topic resolution -------------------------------------------------

    @Test
    void topicFor_mapsEachAggregateToItsV1Topic() {
        assertThat(MasterOutboxPublisher.topicFor("master.warehouse.created")).isEqualTo("wms.master.warehouse.v1");
        assertThat(MasterOutboxPublisher.topicFor("master.zone.updated")).isEqualTo("wms.master.zone.v1");
        assertThat(MasterOutboxPublisher.topicFor("master.zone.deactivated")).isEqualTo("wms.master.zone.v1");
        assertThat(MasterOutboxPublisher.topicFor("master.location.reactivated")).isEqualTo("wms.master.location.v1");
        assertThat(MasterOutboxPublisher.topicFor("master.sku.created")).isEqualTo("wms.master.sku.v1");
        assertThat(MasterOutboxPublisher.topicFor("master.partner.created")).isEqualTo("wms.master.partner.v1");
        assertThat(MasterOutboxPublisher.topicFor("master.lot.expired")).isEqualTo("wms.master.lot.v1");
    }

    @Test
    void topicFor_rejectsUnmappedEventTypes() {
        assertThatThrownBy(() -> MasterOutboxPublisher.topicFor(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MasterOutboxPublisher.topicFor("inventory.stock.moved"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MasterOutboxPublisher.topicFor("master"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MasterOutboxPublisher.topicFor("master.warehouse"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- AC-2 / AC-4: real publish round-trip -----------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_sendsToMappedTopicWithHeaders_thenMarksPublished_andRecordsMetrics() {
        MasterOutboxRepository repository = mock(MasterOutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        MasterOutboxEntity row = new MasterOutboxEntity(
                eventId, "master.warehouse.created", "warehouse", "agg-1",
                null, "{\"envelope\":true}", OCCURRED);

        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));
        given(repository.findById(eventId)).willReturn(Optional.of(row));
        given(repository.countByPublishedAtIsNull()).willReturn(0L);
        given(kafka.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        MasterOutboxPublisher publisher = new MasterOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertThat(sent.topic()).isEqualTo("wms.master.warehouse.v1");
        // partition_key null → key falls back to aggregateId (per-aggregate order)
        assertThat(sent.key()).isEqualTo("agg-1");
        assertThat(sent.value()).isEqualTo("{\"envelope\":true}");
        assertThat(new String(sent.headers().lastHeader("eventId").value())).isEqualTo(eventId.toString());
        assertThat(new String(sent.headers().lastHeader("eventType").value()))
                .isEqualTo("master.warehouse.created");

        // mark-published
        assertThat(row.getPublishedAt()).isEqualTo(CLOCK.instant());
        verify(repository).save(row);

        // master-prefixed metrics (lag = publish - occurred = 2s)
        assertThat(registry.find("master.outbox.publish.success.total")
                .tag("event_type", "master.warehouse.created").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("master.outbox.lag.seconds").timer().count()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructor_registersPreservedPendingCountGauge() {
        MasterOutboxRepository repository = mock(MasterOutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        given(repository.countByPublishedAtIsNull()).willReturn(3L);

        new MasterOutboxPublisher(repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        io.micrometer.core.instrument.Gauge gauge =
                registry.find("master.outbox.pending.count").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(3.0d);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_kafkaFailure_leavesRowPending_recordsFailure_andBacksOff() {
        MasterOutboxRepository repository = mock(MasterOutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        MasterOutboxEntity row = new MasterOutboxEntity(
                eventId, "master.warehouse.created", "warehouse", "agg-1",
                null, "{}", OCCURRED);
        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafka.send(any(ProducerRecord.class))).willReturn(failed);

        MasterOutboxPublisher publisher = new MasterOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending(); // first tick fails → opens backoff window
        publisher.publishPending(); // same instant → suppressed by backoff

        assertThat(row.getPublishedAt()).isNull();
        verify(kafka).send(any(ProducerRecord.class)); // exactly once: second tick suppressed
        verify(repository, never()).save(any());
        assertThat(registry.find("master.outbox.publish.failure.total").counter().count()).isEqualTo(1.0d);
    }

    /**
     * Synchronous {@link TransactionTemplate} stub that runs callbacks inline —
     * the real TX semantics are exercised by the integration suite.
     */
    private static final class SyncTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(mock(TransactionStatus.class));
        }

        @Override
        public void executeWithoutResult(java.util.function.Consumer<TransactionStatus> action) {
            action.accept(mock(TransactionStatus.class));
        }
    }
}

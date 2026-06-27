package com.example.erp.masterdata.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.erp.masterdata.infrastructure.persistence.jpa.MasterdataOutboxJpaEntity;
import com.example.erp.masterdata.infrastructure.persistence.jpa.MasterdataOutboxJpaRepository;
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
 * Unit test for {@link MasterdataOutboxPublisher} (TASK-ERP-BE-026 — outbox v2).
 *
 * <p>The generic poll/backoff/header mechanics live in (and are tested by)
 * {@code AbstractOutboxPublisher}. This test pins the masterdata-service
 * specifics: the five {@code erp.masterdata.* → ….v1} topic mappings (incl.
 * reject-unmapped), the v2 record headers + topic + preserved key/value on a real
 * publish, mark-published, the {@code masterdata}-prefixed metrics, the new
 * {@code masterdata.outbox.pending.count} gauge, and the <b>preserved</b>
 * {@code masterdata_outbox_publish_failures_total} counter (the v1
 * {@code onKafkaSendFailure} hook).
 */
class MasterdataOutboxPublisherTest {

    private static final Instant OCCURRED = Instant.parse("2026-06-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T00:00:02Z"), ZoneOffset.UTC);

    // --- topic resolution -------------------------------------------------------

    @Test
    void topicFor_mapsEachEventTypeToItsPreservedV1Topic() {
        assertThat(MasterdataOutboxPublisher.topicFor("erp.masterdata.department.changed"))
                .isEqualTo("erp.masterdata.department.changed.v1");
        assertThat(MasterdataOutboxPublisher.topicFor("erp.masterdata.employee.changed"))
                .isEqualTo("erp.masterdata.employee.changed.v1");
        assertThat(MasterdataOutboxPublisher.topicFor("erp.masterdata.jobgrade.changed"))
                .isEqualTo("erp.masterdata.jobgrade.changed.v1");
        assertThat(MasterdataOutboxPublisher.topicFor("erp.masterdata.costcenter.changed"))
                .isEqualTo("erp.masterdata.costcenter.changed.v1");
        assertThat(MasterdataOutboxPublisher.topicFor("erp.masterdata.businesspartner.changed"))
                .isEqualTo("erp.masterdata.businesspartner.changed.v1");
    }

    @Test
    void topicFor_rejectsUnmappedEventTypes() {
        assertThatThrownBy(() -> MasterdataOutboxPublisher.topicFor(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MasterdataOutboxPublisher.topicFor("OrderPlaced"))
                .isInstanceOf(IllegalArgumentException.class);
        // already-suffixed must NOT double-resolve
        assertThatThrownBy(() -> MasterdataOutboxPublisher.topicFor("erp.masterdata.department.changed.v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- real publish round-trip ------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_sendsToMappedTopicWithHeaders_preservesKeyAndValue_thenMarksPublished() {
        MasterdataOutboxJpaRepository repository = mock(MasterdataOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"erp.masterdata.department.changed\"}";
        MasterdataOutboxJpaEntity row = MasterdataOutboxJpaEntity.create(
                eventId, "department", "d-1", "erp.masterdata.department.changed",
                payload, "d-1", OCCURRED);

        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));
        given(repository.findById(eventId)).willReturn(Optional.of(row));
        given(repository.countByPublishedAtIsNull()).willReturn(0L);
        given(kafka.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        MasterdataOutboxPublisher publisher = new MasterdataOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertThat(sent.topic()).isEqualTo("erp.masterdata.department.changed.v1");
        // partition_key = aggregateId → key preserves the v1 key
        assertThat(sent.key()).isEqualTo("d-1");
        // value byte-identical to the stored envelope payload
        assertThat(sent.value()).isEqualTo(payload);
        assertThat(new String(sent.headers().lastHeader("eventId").value())).isEqualTo(eventId.toString());
        assertThat(new String(sent.headers().lastHeader("eventType").value()))
                .isEqualTo("erp.masterdata.department.changed");

        // mark-published
        assertThat(row.getPublishedAt()).isEqualTo(CLOCK.instant());
        verify(repository).save(row);

        // masterdata-prefixed metrics (lag = publish - occurred = 2s)
        assertThat(registry.find("masterdata.outbox.publish.success.total")
                .tag("event_type", "erp.masterdata.department.changed").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("masterdata.outbox.lag.seconds").timer().count()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructor_registersPendingCountGauge() {
        MasterdataOutboxJpaRepository repository = mock(MasterdataOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        given(repository.countByPublishedAtIsNull()).willReturn(4L);

        new MasterdataOutboxPublisher(repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        io.micrometer.core.instrument.Gauge gauge =
                registry.find("masterdata.outbox.pending.count").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(4.0d);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_kafkaFailure_leavesRowPending_recordsBothFailureMetrics_andBacksOff() {
        MasterdataOutboxJpaRepository repository = mock(MasterdataOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        MasterdataOutboxJpaEntity row = MasterdataOutboxJpaEntity.create(
                eventId, "employee", "e-1", "erp.masterdata.employee.changed",
                "{}", "e-1", OCCURRED);
        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafka.send(any(ProducerRecord.class))).willReturn(failed);

        MasterdataOutboxPublisher publisher = new MasterdataOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending(); // first tick fails → opens backoff window
        publisher.publishPending(); // same instant → suppressed by backoff

        assertThat(row.getPublishedAt()).isNull();
        verify(kafka).send(any(ProducerRecord.class)); // exactly once: second tick suppressed
        verify(repository, never()).save(any());
        // lib metric
        assertThat(registry.find("masterdata.outbox.publish.failure.total").counter().count()).isEqualTo(1.0d);
        // preserved v1 counter (onKafkaSendFailure hook)
        assertThat(registry.find("masterdata_outbox_publish_failures_total").counter().count()).isEqualTo(1.0d);
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

package com.example.scmplatform.procurement.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.scmplatform.procurement.infrastructure.persistence.jpa.ProcurementOutboxJpaEntity;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.ProcurementOutboxJpaRepository;
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
 * Unit test for {@link ProcurementOutboxPublisher} (TASK-SCM-BE-032, outbox v2).
 *
 * <p>The generic poll/backoff/header mechanics live in (and are tested by)
 * {@code AbstractOutboxPublisher}. This test pins the procurement-service
 * specifics: the seven {@code scm.procurement.* → ….v1} topic mappings (AC-1,
 * incl. reject-unmapped), the v2 record headers + topic + preserved key/value on
 * a real publish (AC-2 / AC-3), mark-published, the {@code procurement}-prefixed
 * metrics, the new {@code procurement.outbox.pending.count} gauge, and the
 * <b>preserved</b> {@code procurement_outbox_publish_failures_total} counter
 * (the v1 {@code onKafkaSendFailure} hook).
 */
class ProcurementOutboxPublisherTest {

    private static final Instant OCCURRED = Instant.parse("2026-06-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T00:00:02Z"), ZoneOffset.UTC);

    // --- AC-1: topic resolution -------------------------------------------------

    @Test
    void topicFor_mapsEachEventTypeToItsPreservedV1Topic() {
        assertThat(ProcurementOutboxPublisher.topicFor("scm.procurement.po.submitted"))
                .isEqualTo("scm.procurement.po.submitted.v1");
        assertThat(ProcurementOutboxPublisher.topicFor("scm.procurement.po.acknowledged"))
                .isEqualTo("scm.procurement.po.acknowledged.v1");
        assertThat(ProcurementOutboxPublisher.topicFor("scm.procurement.po.confirmed"))
                .isEqualTo("scm.procurement.po.confirmed.v1");
        assertThat(ProcurementOutboxPublisher.topicFor("scm.procurement.po.canceled"))
                .isEqualTo("scm.procurement.po.canceled.v1");
        assertThat(ProcurementOutboxPublisher.topicFor("scm.procurement.po.received"))
                .isEqualTo("scm.procurement.po.received.v1");
        assertThat(ProcurementOutboxPublisher.topicFor("scm.procurement.po.closed"))
                .isEqualTo("scm.procurement.po.closed.v1");
        assertThat(ProcurementOutboxPublisher.topicFor("scm.procurement.asn.received"))
                .isEqualTo("scm.procurement.asn.received.v1");
        // ADR-MONO-050 inbound-expected family
        assertThat(ProcurementOutboxPublisher.topicFor("scm.procurement.inbound-expected"))
                .isEqualTo("scm.procurement.inbound-expected.v1");
        assertThat(ProcurementOutboxPublisher.topicFor("scm.procurement.inbound-expected.cancelled"))
                .isEqualTo("scm.procurement.inbound-expected.cancelled.v1");
    }

    @Test
    void topicFor_rejectsUnmappedEventTypes() {
        assertThatThrownBy(() -> ProcurementOutboxPublisher.topicFor(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProcurementOutboxPublisher.topicFor("OrderPlaced"))
                .isInstanceOf(IllegalArgumentException.class);
        // already-suffixed must NOT double-resolve
        assertThatThrownBy(() -> ProcurementOutboxPublisher.topicFor("scm.procurement.po.submitted.v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- AC-2 / AC-3: real publish round-trip -----------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_sendsToMappedTopicWithHeaders_preservesKeyAndValue_thenMarksPublished() {
        ProcurementOutboxJpaRepository repository = mock(ProcurementOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"scm.procurement.po.submitted\"}";
        ProcurementOutboxJpaEntity row = new ProcurementOutboxJpaEntity(
                eventId, "scm.procurement.po.submitted", "purchase_order", "po-1",
                null, payload, OCCURRED);

        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));
        given(repository.findById(eventId)).willReturn(Optional.of(row));
        given(repository.countByPublishedAtIsNull()).willReturn(0L);
        given(kafka.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        ProcurementOutboxPublisher publisher = new ProcurementOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertThat(sent.topic()).isEqualTo("scm.procurement.po.submitted.v1");
        // partition_key null → key falls back to aggregateId (poId) — preserves v1 key
        assertThat(sent.key()).isEqualTo("po-1");
        // value byte-identical to the stored envelope payload
        assertThat(sent.value()).isEqualTo(payload);
        assertThat(new String(sent.headers().lastHeader("eventId").value())).isEqualTo(eventId.toString());
        assertThat(new String(sent.headers().lastHeader("eventType").value())).isEqualTo("scm.procurement.po.submitted");

        // mark-published
        assertThat(row.getPublishedAt()).isEqualTo(CLOCK.instant());
        verify(repository).save(row);

        // procurement-prefixed metrics (lag = publish - occurred = 2s)
        assertThat(registry.find("procurement.outbox.publish.success.total")
                .tag("event_type", "scm.procurement.po.submitted").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("procurement.outbox.lag.seconds").timer().count()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructor_registersPendingCountGauge() {
        ProcurementOutboxJpaRepository repository = mock(ProcurementOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        given(repository.countByPublishedAtIsNull()).willReturn(4L);

        new ProcurementOutboxPublisher(repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        io.micrometer.core.instrument.Gauge gauge =
                registry.find("procurement.outbox.pending.count").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(4.0d);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_kafkaFailure_leavesRowPending_recordsBothFailureMetrics_andBacksOff() {
        ProcurementOutboxJpaRepository repository = mock(ProcurementOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        ProcurementOutboxJpaEntity row = new ProcurementOutboxJpaEntity(
                eventId, "scm.procurement.po.canceled", "purchase_order", "po-1",
                null, "{}", OCCURRED);
        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafka.send(any(ProducerRecord.class))).willReturn(failed);

        ProcurementOutboxPublisher publisher = new ProcurementOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending(); // first tick fails → opens backoff window
        publisher.publishPending(); // same instant → suppressed by backoff

        assertThat(row.getPublishedAt()).isNull();
        verify(kafka).send(any(ProducerRecord.class)); // exactly once: second tick suppressed
        verify(repository, never()).save(any());
        // lib metric
        assertThat(registry.find("procurement.outbox.publish.failure.total").counter().count()).isEqualTo(1.0d);
        // preserved v1 counter (onKafkaSendFailure hook)
        assertThat(registry.find("procurement_outbox_publish_failures_total").counter().count()).isEqualTo(1.0d);
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

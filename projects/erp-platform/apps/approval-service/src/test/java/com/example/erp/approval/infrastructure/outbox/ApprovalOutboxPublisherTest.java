package com.example.erp.approval.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.erp.approval.infrastructure.persistence.jpa.ApprovalOutboxJpaEntity;
import com.example.erp.approval.infrastructure.persistence.jpa.ApprovalOutboxJpaRepository;
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
 * Unit test for {@link ApprovalOutboxPublisher} (TASK-ERP-BE-025 — outbox v2).
 *
 * <p>The generic poll/backoff/header mechanics live in (and are tested by)
 * {@code AbstractOutboxPublisher}. This test pins the approval-service specifics:
 * the SIX {@code erp.approval.* → ….v1} topic mappings (incl. the two delegation
 * topics the v1 scheduler omitted — the delegation-gap fix — and reject-unmapped),
 * the v2 record headers + topic + preserved key/value on a real publish,
 * mark-published, the {@code approval}-prefixed metrics, the new
 * {@code approval.outbox.pending.count} gauge, and the <b>preserved</b>
 * {@code approval_outbox_publish_failures_total} counter (the v1
 * {@code onKafkaSendFailure} hook).
 */
class ApprovalOutboxPublisherTest {

    private static final Instant OCCURRED = Instant.parse("2026-06-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T00:00:02Z"), ZoneOffset.UTC);

    // --- topic resolution (incl. delegation-gap fix) ----------------------------

    @Test
    void topicFor_mapsAllSixEventTypesToTheirPreservedV1Topics() {
        assertThat(ApprovalOutboxPublisher.topicFor("erp.approval.submitted"))
                .isEqualTo("erp.approval.submitted.v1");
        assertThat(ApprovalOutboxPublisher.topicFor("erp.approval.approved"))
                .isEqualTo("erp.approval.approved.v1");
        assertThat(ApprovalOutboxPublisher.topicFor("erp.approval.rejected"))
                .isEqualTo("erp.approval.rejected.v1");
        assertThat(ApprovalOutboxPublisher.topicFor("erp.approval.withdrawn"))
                .isEqualTo("erp.approval.withdrawn.v1");
        // delegation-gap fix: the v1 scheduler omitted these two (poison-pill).
        assertThat(ApprovalOutboxPublisher.topicFor("erp.approval.delegated"))
                .isEqualTo("erp.approval.delegated.v1");
        assertThat(ApprovalOutboxPublisher.topicFor("erp.approval.delegation.revoked"))
                .isEqualTo("erp.approval.delegation.revoked.v1");
    }

    @Test
    void topicFor_rejectsUnmappedEventTypes() {
        assertThatThrownBy(() -> ApprovalOutboxPublisher.topicFor(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApprovalOutboxPublisher.topicFor("OrderPlaced"))
                .isInstanceOf(IllegalArgumentException.class);
        // already-suffixed must NOT double-resolve
        assertThatThrownBy(() -> ApprovalOutboxPublisher.topicFor("erp.approval.submitted.v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- real publish round-trip ------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_sendsToMappedTopicWithHeaders_preservesKeyAndValue_thenMarksPublished() {
        ApprovalOutboxJpaRepository repository = mock(ApprovalOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"erp.approval.submitted\"}";
        ApprovalOutboxJpaEntity row = ApprovalOutboxJpaEntity.create(
                eventId, "ApprovalRequest", "appr-1", "erp.approval.submitted",
                payload, "appr-1", OCCURRED);

        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));
        given(repository.findById(eventId)).willReturn(Optional.of(row));
        given(repository.countByPublishedAtIsNull()).willReturn(0L);
        given(kafka.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        ApprovalOutboxPublisher publisher = new ApprovalOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertThat(sent.topic()).isEqualTo("erp.approval.submitted.v1");
        // partition_key = aggregateId → key preserves the v1 key
        assertThat(sent.key()).isEqualTo("appr-1");
        // value byte-identical to the stored envelope payload
        assertThat(sent.value()).isEqualTo(payload);
        assertThat(new String(sent.headers().lastHeader("eventId").value())).isEqualTo(eventId.toString());
        assertThat(new String(sent.headers().lastHeader("eventType").value())).isEqualTo("erp.approval.submitted");

        // mark-published
        assertThat(row.getPublishedAt()).isEqualTo(CLOCK.instant());
        verify(repository).save(row);

        // approval-prefixed metrics (lag = publish - occurred = 2s)
        assertThat(registry.find("approval.outbox.publish.success.total")
                .tag("event_type", "erp.approval.submitted").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("approval.outbox.lag.seconds").timer().count()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructor_registersPendingCountGauge() {
        ApprovalOutboxJpaRepository repository = mock(ApprovalOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        given(repository.countByPublishedAtIsNull()).willReturn(4L);

        new ApprovalOutboxPublisher(repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        io.micrometer.core.instrument.Gauge gauge =
                registry.find("approval.outbox.pending.count").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(4.0d);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_kafkaFailure_leavesRowPending_recordsBothFailureMetrics_andBacksOff() {
        ApprovalOutboxJpaRepository repository = mock(ApprovalOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        ApprovalOutboxJpaEntity row = ApprovalOutboxJpaEntity.create(
                eventId, "DelegationGrant", "dgr-1", "erp.approval.delegated",
                "{}", "dgr-1", OCCURRED);
        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafka.send(any(ProducerRecord.class))).willReturn(failed);

        ApprovalOutboxPublisher publisher = new ApprovalOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending(); // first tick fails → opens backoff window
        publisher.publishPending(); // same instant → suppressed by backoff

        assertThat(row.getPublishedAt()).isNull();
        verify(kafka).send(any(ProducerRecord.class)); // exactly once: second tick suppressed
        verify(repository, never()).save(any());
        // lib metric
        assertThat(registry.find("approval.outbox.publish.failure.total").counter().count()).isEqualTo(1.0d);
        // preserved v1 counter (onKafkaSendFailure hook)
        assertThat(registry.find("approval_outbox_publish_failures_total").counter().count()).isEqualTo(1.0d);
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

package com.example.settlement.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.settlement.application.event.SettlementPeriodClosedEvent;
import com.example.settlement.infrastructure.persistence.SettlementOutboxEntity;
import com.example.settlement.infrastructure.persistence.SettlementOutboxRepository;
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
 * Unit test for {@link SettlementOutboxPublisher} (TASK-BE-447, outbox v2). Pins the
 * settlement-service specifics: the {@code settlement.period.closed.v1 →
 * settlement.period.closed} topic mapping (incl. reject-unmapped), the v2 headers +
 * topic + preserved key/value on a real publish, mark-published, {@code settlement}-
 * prefixed metrics, the pending-count gauge, and kafka-failure backoff.
 */
class SettlementOutboxPublisherTest {

    private static final Instant OCCURRED = Instant.parse("2026-06-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T00:00:02Z"), ZoneOffset.UTC);

    @Test
    void topicFor_mapsEventTypeToPreservedTopic() {
        assertThat(SettlementOutboxPublisher.topicFor(SettlementPeriodClosedEvent.EVENT_TYPE))
                .isEqualTo("settlement.period.closed");
    }

    @Test
    void topicFor_rejectsUnmappedEventTypes() {
        assertThatThrownBy(() -> SettlementOutboxPublisher.topicFor(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettlementOutboxPublisher.topicFor("settlement.period.closed"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettlementOutboxPublisher.topicFor("OrderPlaced"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_sendsToMappedTopicWithHeaders_preservesKeyAndValue_thenMarksPublished() {
        SettlementOutboxRepository repository = mock(SettlementOutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        String payload = "{\"event_id\":\"" + eventId + "\"}";
        SettlementOutboxEntity row = new SettlementOutboxEntity(
                eventId, SettlementPeriodClosedEvent.EVENT_TYPE, "SettlementPeriod",
                "period-1", null, payload, OCCURRED);

        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));
        given(repository.findById(eventId)).willReturn(Optional.of(row));
        given(repository.countByPublishedAtIsNull()).willReturn(0L);
        given(kafka.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        SettlementOutboxPublisher publisher = new SettlementOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertThat(sent.topic()).isEqualTo("settlement.period.closed");
        assertThat(sent.key()).isEqualTo("period-1");
        assertThat(sent.value()).isEqualTo(payload);
        assertThat(new String(sent.headers().lastHeader("eventId").value())).isEqualTo(eventId.toString());
        assertThat(new String(sent.headers().lastHeader("eventType").value()))
                .isEqualTo(SettlementPeriodClosedEvent.EVENT_TYPE);

        assertThat(row.getPublishedAt()).isEqualTo(CLOCK.instant());
        verify(repository).save(row);

        assertThat(registry.find("settlement.outbox.publish.success.total")
                .tag("event_type", SettlementPeriodClosedEvent.EVENT_TYPE).counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("settlement.outbox.lag.seconds").timer().count()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructor_registersPreservedPendingCountGauge() {
        SettlementOutboxRepository repository = mock(SettlementOutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        given(repository.countByPublishedAtIsNull()).willReturn(5L);

        new SettlementOutboxPublisher(repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        io.micrometer.core.instrument.Gauge gauge =
                registry.find("settlement.outbox.pending.count").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(5.0d);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_kafkaFailure_leavesRowPending_recordsFailure_andBacksOff() {
        SettlementOutboxRepository repository = mock(SettlementOutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        SettlementOutboxEntity row = new SettlementOutboxEntity(
                eventId, SettlementPeriodClosedEvent.EVENT_TYPE, "SettlementPeriod",
                "period-1", null, "{}", OCCURRED);
        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafka.send(any(ProducerRecord.class))).willReturn(failed);

        SettlementOutboxPublisher publisher = new SettlementOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending();
        publisher.publishPending();

        assertThat(row.getPublishedAt()).isNull();
        verify(kafka).send(any(ProducerRecord.class));
        verify(repository, never()).save(any());
        assertThat(registry.find("settlement.outbox.publish.failure.total").counter().count()).isEqualTo(1.0d);
    }

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

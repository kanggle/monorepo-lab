package com.example.order.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.order.application.port.OrderMetricsPort;
import com.example.order.infrastructure.persistence.OrderOutboxEntity;
import com.example.order.infrastructure.persistence.OrderOutboxRepository;
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
 * Unit test for {@link OrderOutboxPublisher} (TASK-BE-448, outbox v2). Pins the
 * order-service specifics: the 4-event topic mapping (incl. reject-unmapped), the
 * v2 headers + topic + preserved key/value on a real publish, mark-published,
 * {@code order}-prefixed metrics, the preserved pending-count gauge, the kafka-failure
 * backoff, AND the preserved v1 {@code event_publish_failure_total} hook via
 * {@link OrderMetricsPort#recordEventPublishFailure}.
 */
class OrderOutboxPublisherTest {

    private static final Instant OCCURRED = Instant.parse("2026-06-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T00:00:02Z"), ZoneOffset.UTC);

    @Test
    void topicFor_mapsEachEventTypeToItsPreservedTopic() {
        assertThat(OrderOutboxPublisher.topicFor("OrderPlaced")).isEqualTo("order.order.placed");
        assertThat(OrderOutboxPublisher.topicFor("OrderConfirmed")).isEqualTo("order.order.confirmed");
        assertThat(OrderOutboxPublisher.topicFor("OrderCancelled")).isEqualTo("order.order.cancelled");
        assertThat(OrderOutboxPublisher.topicFor("OrderSagaRecoveryExhausted"))
                .isEqualTo("order.alert.saga.recovery.exhausted");
    }

    @Test
    void topicFor_rejectsUnmappedEventTypes() {
        assertThatThrownBy(() -> OrderOutboxPublisher.topicFor(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderOutboxPublisher.topicFor("PaymentCompleted"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_sendsToMappedTopicWithHeaders_preservesKeyAndValue_thenMarksPublished() {
        OrderOutboxRepository repository = mock(OrderOutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        OrderMetricsPort orderMetrics = mock(OrderMetricsPort.class);

        UUID eventId = UUID.randomUUID();
        String payload = "{\"event_id\":\"" + eventId + "\"}";
        OrderOutboxEntity row = new OrderOutboxEntity(
                eventId, "OrderPlaced", "Order", "order-1", null, payload, OCCURRED);

        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));
        given(repository.findById(eventId)).willReturn(Optional.of(row));
        given(repository.countByPublishedAtIsNull()).willReturn(0L);
        given(kafka.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        OrderOutboxPublisher publisher = new OrderOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, orderMetrics, 100);

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertThat(sent.topic()).isEqualTo("order.order.placed");
        assertThat(sent.key()).isEqualTo("order-1");
        assertThat(sent.value()).isEqualTo(payload);
        assertThat(new String(sent.headers().lastHeader("eventId").value())).isEqualTo(eventId.toString());
        assertThat(new String(sent.headers().lastHeader("eventType").value())).isEqualTo("OrderPlaced");

        assertThat(row.getPublishedAt()).isEqualTo(CLOCK.instant());
        verify(repository).save(row);

        assertThat(registry.find("order.outbox.publish.success.total")
                .tag("event_type", "OrderPlaced").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("order.outbox.lag.seconds").timer().count()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructor_registersPreservedPendingCountGauge() {
        OrderOutboxRepository repository = mock(OrderOutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        OrderMetricsPort orderMetrics = mock(OrderMetricsPort.class);
        given(repository.countByPublishedAtIsNull()).willReturn(6L);

        new OrderOutboxPublisher(repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, orderMetrics, 100);

        io.micrometer.core.instrument.Gauge gauge =
                registry.find("order.outbox.pending.count").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(6.0d);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_kafkaFailure_recordsFailure_preservesV1OrderMetricHook_andBacksOff() {
        OrderOutboxRepository repository = mock(OrderOutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        OrderMetricsPort orderMetrics = mock(OrderMetricsPort.class);

        UUID eventId = UUID.randomUUID();
        OrderOutboxEntity row = new OrderOutboxEntity(
                eventId, "OrderConfirmed", "Order", "order-1", null, "{}", OCCURRED);
        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafka.send(any(ProducerRecord.class))).willReturn(failed);

        OrderOutboxPublisher publisher = new OrderOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, orderMetrics, 100);

        publisher.publishPending();
        publisher.publishPending();

        assertThat(row.getPublishedAt()).isNull();
        verify(kafka).send(any(ProducerRecord.class));
        verify(repository, never()).save(any());
        assertThat(registry.find("order.outbox.publish.failure.total").counter().count()).isEqualTo(1.0d);
        // v1 hook preserved: per-event send failure also bumps event_publish_failure_total
        verify(orderMetrics).recordEventPublishFailure(eq("OrderConfirmed"));
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

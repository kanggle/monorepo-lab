package com.wms.notification.adapter.outbound.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wms.notification.adapter.outbound.persistence.jpa.outbox.NotificationOutboxJpaEntity;
import com.wms.notification.adapter.outbound.persistence.jpa.outbox.NotificationOutboxJpaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit coverage for {@link OutboxPollingScheduler}'s topic resolver (TASK-BE-524).
 *
 * <p>The published-event contract ({@code specs/contracts/events/notification-events.md}
 * § Out of Scope) states: <em>"{@code notification.delivery.scheduled} is NOT published to
 * Kafka in v1 — the outbox writes the row but the publisher's topic resolver only forwards
 * {@code notification.delivered}"</em>. The poller previously had <b>no such resolver</b> and
 * forwarded EVERY unpublished row to {@code wms.notification.delivered.v1}, so
 * {@code .scheduled} events reached Kafka contrary to the contract — and no test asserted
 * what actually lands on the topic, which is why the mismatch stayed invisible.
 *
 * <p>These tests drive the real poller with a mocked {@link KafkaTemplate} and capture the
 * actual {@link ProducerRecord}s sent, so the assertion is on observed runtime behaviour
 * rather than a static reading of the code.
 */
class OutboxPollingSchedulerTest {

    private static final String TOPIC = "wms.notification.delivered.v1";
    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");

    private NotificationOutboxJpaRepository repository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private SimpleMeterRegistry meterRegistry;
    private OutboxPollingScheduler scheduler;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        repository = mock(NotificationOutboxJpaRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        meterRegistry = new SimpleMeterRegistry();

        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // TransactionTemplate stub that simply runs the callback inline.
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());

        scheduler = new OutboxPollingScheduler(repository, kafkaTemplate, tx,
                Clock.fixed(NOW, ZoneOffset.UTC), meterRegistry, 100, TOPIC);
    }

    private NotificationOutboxJpaEntity row(String eventType) {
        return new NotificationOutboxJpaEntity(
                UUID.randomUUID(), "notification.delivery", UUID.randomUUID().toString(),
                eventType, "1", "{\"eventType\":\"" + eventType + "\"}",
                UUID.randomUUID().toString(), NOW);
    }

    private void givenPending(NotificationOutboxJpaEntity... rows) {
        when(repository.findPending(any(Pageable.class))).thenReturn(List.of(rows));
        for (NotificationOutboxJpaEntity r : rows) {
            when(repository.findById(r.getId())).thenReturn(Optional.of(r));
        }
    }

    @Test
    @DisplayName("only notification.delivered is forwarded to Kafka — .scheduled is not (contract § Out of Scope)")
    void onlyDeliveredIsForwardedToKafka() {
        NotificationOutboxJpaEntity scheduled = row("notification.delivery.scheduled");
        NotificationOutboxJpaEntity delivered = row("notification.delivered");
        givenPending(scheduled, delivered);

        scheduler.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(1)).send(sent.capture());

        ProducerRecord<String, String> record = sent.getValue();
        assertThat(record.topic()).isEqualTo(TOPIC);
        assertThat(new String(record.headers().lastHeader("eventType").value()))
                .as("the single forwarded record must be notification.delivered")
                .isEqualTo("notification.delivered");
        assertThat(record.value()).contains("notification.delivered");
    }

    @Test
    @DisplayName(".scheduled row is drained (marked published) so it is not re-polled forever")
    void scheduledRowIsDrainedWithoutForwarding() {
        NotificationOutboxJpaEntity scheduled = row("notification.delivery.scheduled");
        givenPending(scheduled);

        scheduler.publishPending();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        assertThat(scheduled.getPublishedAt())
                .as("drained rows must be marked published, otherwise findPending returns them every poll")
                .isEqualTo(NOW);
        verify(repository, times(1)).save(scheduled);
    }

    @Test
    @DisplayName("delivered row is forwarded AND marked published")
    void deliveredRowIsForwardedAndMarkedPublished() {
        NotificationOutboxJpaEntity delivered = row("notification.delivered");
        givenPending(delivered);

        scheduler.publishPending();

        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        assertThat(delivered.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("metrics: forwarded rows count as publish-success, drained rows as skipped")
    void metricsSeparateForwardedFromSkipped() {
        givenPending(row("notification.delivery.scheduled"), row("notification.delivered"));

        scheduler.publishPending();

        assertThat(meterRegistry.counter("notification.outbox.publish.success.total").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter("notification.outbox.skipped.total").count())
                .isEqualTo(1.0);
    }
}

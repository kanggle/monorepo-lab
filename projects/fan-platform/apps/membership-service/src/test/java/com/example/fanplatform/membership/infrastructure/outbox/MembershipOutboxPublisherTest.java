package com.example.fanplatform.membership.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.fanplatform.membership.infrastructure.jpa.MembershipOutboxJpaEntity;
import com.example.fanplatform.membership.infrastructure.jpa.MembershipOutboxJpaRepository;
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
 * Unit test for {@link MembershipOutboxPublisher} (TASK-FAN-BE-020, outbox v2).
 *
 * <p>The generic poll/backoff/header mechanics live in (and are tested by)
 * {@code AbstractOutboxPublisher}. This test pins the membership-service
 * specifics: the three {@code fan.membership.* → ….v1} topic mappings (incl.
 * reject-unmapped), the v2 record headers + topic + preserved key/value on a real
 * publish, mark-published, the {@code membership}-prefixed metrics, the new
 * {@code membership.outbox.pending.count} gauge, and the <b>preserved</b>
 * {@code membership_outbox_publish_failures_total} counter (the v1
 * {@code onKafkaSendFailure} hook).
 */
class MembershipOutboxPublisherTest {

    private static final Instant OCCURRED = Instant.parse("2026-06-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T00:00:02Z"), ZoneOffset.UTC);

    @Test
    void topicFor_mapsEachEventTypeToItsPreservedV1Topic() {
        assertThat(MembershipOutboxPublisher.topicFor("fan.membership.activated"))
                .isEqualTo("fan.membership.activated.v1");
        assertThat(MembershipOutboxPublisher.topicFor("fan.membership.canceled"))
                .isEqualTo("fan.membership.canceled.v1");
        assertThat(MembershipOutboxPublisher.topicFor("fan.membership.expired"))
                .isEqualTo("fan.membership.expired.v1");
    }

    @Test
    void topicFor_rejectsUnmappedEventTypes() {
        assertThatThrownBy(() -> MembershipOutboxPublisher.topicFor(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipOutboxPublisher.topicFor("OrderPlaced"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipOutboxPublisher.topicFor("fan.membership.activated.v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_sendsToMappedTopicWithHeaders_preservesKeyAndValue_thenMarksPublished() {
        MembershipOutboxJpaRepository repository = mock(MembershipOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"fan.membership.activated\"}";
        MembershipOutboxJpaEntity row = new MembershipOutboxJpaEntity(
                eventId, "fan.membership.activated", "membership", "m1",
                null, payload, OCCURRED);

        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));
        given(repository.findById(eventId)).willReturn(Optional.of(row));
        given(repository.countByPublishedAtIsNull()).willReturn(0L);
        given(kafka.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        MembershipOutboxPublisher publisher = new MembershipOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertThat(sent.topic()).isEqualTo("fan.membership.activated.v1");
        assertThat(sent.key()).isEqualTo("m1");
        assertThat(sent.value()).isEqualTo(payload);
        assertThat(new String(sent.headers().lastHeader("eventId").value())).isEqualTo(eventId.toString());
        assertThat(new String(sent.headers().lastHeader("eventType").value())).isEqualTo("fan.membership.activated");

        assertThat(row.getPublishedAt()).isEqualTo(CLOCK.instant());
        verify(repository).save(row);

        assertThat(registry.find("membership.outbox.publish.success.total")
                .tag("event_type", "fan.membership.activated").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("membership.outbox.lag.seconds").timer().count()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructor_registersPendingCountGauge() {
        MembershipOutboxJpaRepository repository = mock(MembershipOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        given(repository.countByPublishedAtIsNull()).willReturn(4L);

        new MembershipOutboxPublisher(repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        io.micrometer.core.instrument.Gauge gauge =
                registry.find("membership.outbox.pending.count").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(4.0d);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_kafkaFailure_leavesRowPending_recordsBothFailureMetrics_andBacksOff() {
        MembershipOutboxJpaRepository repository = mock(MembershipOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        MembershipOutboxJpaEntity row = new MembershipOutboxJpaEntity(
                eventId, "fan.membership.canceled", "membership", "m1",
                null, "{}", OCCURRED);
        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafka.send(any(ProducerRecord.class))).willReturn(failed);

        MembershipOutboxPublisher publisher = new MembershipOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending(); // first tick fails → opens backoff window
        publisher.publishPending(); // same instant → suppressed by backoff

        assertThat(row.getPublishedAt()).isNull();
        verify(kafka).send(any(ProducerRecord.class));
        verify(repository, never()).save(any());
        assertThat(registry.find("membership.outbox.publish.failure.total").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("membership_outbox_publish_failures_total").counter().count()).isEqualTo(1.0d);
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

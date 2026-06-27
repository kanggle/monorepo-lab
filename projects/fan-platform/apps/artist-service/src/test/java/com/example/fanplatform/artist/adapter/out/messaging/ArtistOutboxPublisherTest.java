package com.example.fanplatform.artist.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.fanplatform.artist.adapter.out.persistence.ArtistOutboxJpaEntity;
import com.example.fanplatform.artist.adapter.out.persistence.ArtistOutboxJpaRepository;
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
 * Unit test for {@link ArtistOutboxPublisher} (TASK-FAN-BE-022, outbox v2).
 *
 * <p>Pins the artist-service specifics: the six {@code artist.* → ….v1} topic
 * mappings (incl. reject-unmapped), the v2 record headers + topic + preserved
 * key/value on a real publish, mark-published, the {@code artist}-prefixed
 * metrics, the new {@code artist.outbox.pending.count} gauge, and the
 * <b>preserved</b> {@code artist_outbox_publish_failures_total} counter.
 */
class ArtistOutboxPublisherTest {

    private static final Instant OCCURRED = Instant.parse("2026-06-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T00:00:02Z"), ZoneOffset.UTC);

    @Test
    void topicFor_mapsEachEventTypeToItsPreservedV1Topic() {
        assertThat(ArtistOutboxPublisher.topicFor("artist.registered"))
                .isEqualTo("artist.registered.v1");
        assertThat(ArtistOutboxPublisher.topicFor("artist.published"))
                .isEqualTo("artist.published.v1");
        assertThat(ArtistOutboxPublisher.topicFor("artist.updated"))
                .isEqualTo("artist.updated.v1");
        assertThat(ArtistOutboxPublisher.topicFor("artist.archived"))
                .isEqualTo("artist.archived.v1");
        assertThat(ArtistOutboxPublisher.topicFor("artist.group_created"))
                .isEqualTo("artist.group_created.v1");
        assertThat(ArtistOutboxPublisher.topicFor("artist.group_member_changed"))
                .isEqualTo("artist.group_member_changed.v1");
    }

    @Test
    void topicFor_rejectsUnmappedEventTypes() {
        assertThatThrownBy(() -> ArtistOutboxPublisher.topicFor(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArtistOutboxPublisher.topicFor("OrderPlaced"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArtistOutboxPublisher.topicFor("artist.registered.v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_sendsToMappedTopicWithHeaders_preservesKeyAndValue_thenMarksPublished() {
        ArtistOutboxJpaRepository repository = mock(ArtistOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"artist.registered\"}";
        ArtistOutboxJpaEntity row = new ArtistOutboxJpaEntity(
                eventId, "artist.registered", "artist", "a-1",
                null, payload, OCCURRED);

        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));
        given(repository.findById(eventId)).willReturn(Optional.of(row));
        given(repository.countByPublishedAtIsNull()).willReturn(0L);
        given(kafka.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        ArtistOutboxPublisher publisher = new ArtistOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertThat(sent.topic()).isEqualTo("artist.registered.v1");
        assertThat(sent.key()).isEqualTo("a-1");
        assertThat(sent.value()).isEqualTo(payload);
        assertThat(new String(sent.headers().lastHeader("eventId").value())).isEqualTo(eventId.toString());
        assertThat(new String(sent.headers().lastHeader("eventType").value())).isEqualTo("artist.registered");

        assertThat(row.getPublishedAt()).isEqualTo(CLOCK.instant());
        verify(repository).save(row);

        assertThat(registry.find("artist.outbox.publish.success.total")
                .tag("event_type", "artist.registered").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("artist.outbox.lag.seconds").timer().count()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructor_registersPendingCountGauge() {
        ArtistOutboxJpaRepository repository = mock(ArtistOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        given(repository.countByPublishedAtIsNull()).willReturn(4L);

        new ArtistOutboxPublisher(repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        io.micrometer.core.instrument.Gauge gauge =
                registry.find("artist.outbox.pending.count").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(4.0d);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_kafkaFailure_leavesRowPending_recordsBothFailureMetrics_andBacksOff() {
        ArtistOutboxJpaRepository repository = mock(ArtistOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        ArtistOutboxJpaEntity row = new ArtistOutboxJpaEntity(
                eventId, "artist.archived", "artist", "a-1",
                null, "{}", OCCURRED);
        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafka.send(any(ProducerRecord.class))).willReturn(failed);

        ArtistOutboxPublisher publisher = new ArtistOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending();
        publisher.publishPending();

        assertThat(row.getPublishedAt()).isNull();
        verify(kafka).send(any(ProducerRecord.class));
        verify(repository, never()).save(any());
        assertThat(registry.find("artist.outbox.publish.failure.total").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("artist_outbox_publish_failures_total").counter().count()).isEqualTo(1.0d);
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

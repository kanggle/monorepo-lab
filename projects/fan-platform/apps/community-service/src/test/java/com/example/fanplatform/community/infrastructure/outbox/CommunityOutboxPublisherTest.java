package com.example.fanplatform.community.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.fanplatform.community.infrastructure.jpa.CommunityOutboxJpaEntity;
import com.example.fanplatform.community.infrastructure.jpa.CommunityOutboxJpaRepository;
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
 * Unit test for {@link CommunityOutboxPublisher} (TASK-FAN-BE-021, outbox v2).
 *
 * <p>Pins the community-service specifics: the four {@code community.* → ….v1}
 * topic mappings (incl. reject-unmapped), the v2 record headers + topic +
 * preserved key/value on a real publish, mark-published, the {@code community}-
 * prefixed metrics, the new {@code community.outbox.pending.count} gauge, and the
 * <b>preserved</b> {@code community_outbox_publish_failures_total} counter.
 */
class CommunityOutboxPublisherTest {

    private static final Instant OCCURRED = Instant.parse("2026-06-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T00:00:02Z"), ZoneOffset.UTC);

    @Test
    void topicFor_mapsEachEventTypeToItsPreservedV1Topic() {
        assertThat(CommunityOutboxPublisher.topicFor("community.post.published"))
                .isEqualTo("community.post.published.v1");
        assertThat(CommunityOutboxPublisher.topicFor("community.post.status_changed"))
                .isEqualTo("community.post.status_changed.v1");
        assertThat(CommunityOutboxPublisher.topicFor("community.comment.added"))
                .isEqualTo("community.comment.added.v1");
        assertThat(CommunityOutboxPublisher.topicFor("community.reaction.added"))
                .isEqualTo("community.reaction.added.v1");
    }

    @Test
    void topicFor_rejectsUnmappedEventTypes() {
        assertThatThrownBy(() -> CommunityOutboxPublisher.topicFor(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommunityOutboxPublisher.topicFor("OrderPlaced"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommunityOutboxPublisher.topicFor("community.post.published.v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_sendsToMappedTopicWithHeaders_preservesKeyAndValue_thenMarksPublished() {
        CommunityOutboxJpaRepository repository = mock(CommunityOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"community.post.published\"}";
        CommunityOutboxJpaEntity row = new CommunityOutboxJpaEntity(
                eventId, "community.post.published", "community", "p1",
                null, payload, OCCURRED);

        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));
        given(repository.findById(eventId)).willReturn(Optional.of(row));
        given(repository.countByPublishedAtIsNull()).willReturn(0L);
        given(kafka.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        CommunityOutboxPublisher publisher = new CommunityOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertThat(sent.topic()).isEqualTo("community.post.published.v1");
        assertThat(sent.key()).isEqualTo("p1");
        assertThat(sent.value()).isEqualTo(payload);
        assertThat(new String(sent.headers().lastHeader("eventId").value())).isEqualTo(eventId.toString());
        assertThat(new String(sent.headers().lastHeader("eventType").value())).isEqualTo("community.post.published");

        assertThat(row.getPublishedAt()).isEqualTo(CLOCK.instant());
        verify(repository).save(row);

        assertThat(registry.find("community.outbox.publish.success.total")
                .tag("event_type", "community.post.published").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("community.outbox.lag.seconds").timer().count()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructor_registersPendingCountGauge() {
        CommunityOutboxJpaRepository repository = mock(CommunityOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        given(repository.countByPublishedAtIsNull()).willReturn(4L);

        new CommunityOutboxPublisher(repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        io.micrometer.core.instrument.Gauge gauge =
                registry.find("community.outbox.pending.count").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(4.0d);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_kafkaFailure_leavesRowPending_recordsBothFailureMetrics_andBacksOff() {
        CommunityOutboxJpaRepository repository = mock(CommunityOutboxJpaRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        CommunityOutboxJpaEntity row = new CommunityOutboxJpaEntity(
                eventId, "community.comment.added", "community", "p1",
                null, "{}", OCCURRED);
        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafka.send(any(ProducerRecord.class))).willReturn(failed);

        CommunityOutboxPublisher publisher = new CommunityOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending();
        publisher.publishPending();

        assertThat(row.getPublishedAt()).isNull();
        verify(kafka).send(any(ProducerRecord.class));
        verify(repository, never()).save(any());
        assertThat(registry.find("community.outbox.publish.failure.total").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("community_outbox_publish_failures_total").counter().count()).isEqualTo(1.0d);
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

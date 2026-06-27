package com.example.membership.infrastructure.outbox;

import com.example.common.id.UuidV7;
import com.example.membership.infrastructure.persistence.MembershipOutboxJpaEntity;
import com.example.membership.infrastructure.persistence.MembershipOutboxJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the membership-service v2 relay {@link MembershipOutboxPublisher}
 * (TASK-BE-454 — outbox v1 → v2). Replaces the v1
 * {@code MembershipOutboxPollingSchedulerTest}: verifies the static {@link
 * MembershipOutboxPublisher#topicFor} mapping (all 3 membership.* events →
 * identically-named bare topic, no {@code .v1} suffix; reject-unmapped) and a
 * publish round-trip (Kafka key/value/headers, mark-published, pending gauge).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("MembershipOutboxPublisher 단위 테스트")
class MembershipOutboxPublisherTest {

    @Mock
    private MembershipOutboxJpaRepository repository;

    @Mock
    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, String> kafkaTemplate;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final TransactionTemplate transactionTemplate = new ImmediateTransactionTemplate();

    private MembershipOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        lenient().when(repository.countByPublishedAtIsNull()).thenReturn(0L);
        publisher = new MembershipOutboxPublisher(repository, kafkaTemplate, transactionTemplate,
                Clock.fixed(Instant.parse("2026-04-14T10:00:00Z"), ZoneOffset.UTC),
                meterRegistry, 100);
    }

    @Test
    @DisplayName("topicFor maps each membership.* event to its identically-named bare topic")
    void topicFor_mapsAllMembershipEventTypes() {
        assertThat(MembershipOutboxPublisher.topicFor("membership.subscription.activated"))
                .isEqualTo("membership.subscription.activated");
        assertThat(MembershipOutboxPublisher.topicFor("membership.subscription.expired"))
                .isEqualTo("membership.subscription.expired");
        assertThat(MembershipOutboxPublisher.topicFor("membership.subscription.cancelled"))
                .isEqualTo("membership.subscription.cancelled");
    }

    @Test
    @DisplayName("topicFor rejects an unknown event type")
    void topicFor_unknownEventType_throws() {
        assertThatThrownBy(() -> MembershipOutboxPublisher.topicFor("unknown.event"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown membership event type");
        assertThatThrownBy(() -> MembershipOutboxPublisher.topicFor(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("publishPending sends key=aggregateId, value=payload, eventId/eventType headers, then marks published")
    void publishPending_roundTrip() {
        UUID id = UuidV7.randomUuid();
        MembershipOutboxJpaEntity row = MembershipOutboxJpaEntity.create(
                id, "membership", "acc-1", "membership.subscription.activated",
                "{\"eventId\":\"" + id + "\"}", "acc-1",
                Instant.parse("2026-04-14T09:59:59Z"));

        when(repository.findPending(any())).thenReturn(List.of(row));
        when(repository.findById(id)).thenReturn(Optional.of(row));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(ackFuture("membership.subscription.activated"));

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, String> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("membership.subscription.activated");
        assertThat(record.key()).isEqualTo("acc-1");
        assertThat(record.value()).isEqualTo("{\"eventId\":\"" + id + "\"}");
        assertThat(new String(record.headers().lastHeader("eventId").value(), StandardCharsets.UTF_8))
                .isEqualTo(id.toString());
        assertThat(new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                .isEqualTo("membership.subscription.activated");

        verify(repository).save(row);
        assertThat(row.getPublishedAt()).isNotNull();
    }

    private static CompletableFuture<SendResult<String, String>> ackFuture(String topic) {
        TopicPartition tp = new TopicPartition(topic, 0);
        RecordMetadata md = new RecordMetadata(tp, 0, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(
                new SendResult<>(new ProducerRecord<>(topic, "k", "v"), md));
    }

    /** Executes the callback inline (no real transaction manager needed for the unit test). */
    private static final class ImmediateTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }

        @Override
        public void executeWithoutResult(java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action) {
            action.accept(new SimpleTransactionStatus());
        }
    }
}

package com.example.promotion.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
 * Unit test for {@link PromotionOutboxPublisher} (TASK-BE-444, outbox v2).
 *
 * <p>The generic poll/backoff/header mechanics live in (and are tested by)
 * {@code AbstractOutboxPublisher}. This test pins the promotion-service
 * specifics: the {@code CouponUsed → promotion.coupon.used} /
 * {@code CouponExpired → promotion.coupon.expired} topic mapping (AC-1, incl.
 * reject-unmapped), the v2 record headers + topic + preserved key/value on a
 * real publish (AC-2 / AC-3), mark-published, the {@code promotion}-prefixed
 * metrics, and the preserved {@code promotion.outbox.pending.count} gauge.
 */
class PromotionOutboxPublisherTest {

    private static final Instant OCCURRED = Instant.parse("2026-06-27T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T00:00:02Z"), ZoneOffset.UTC);

    // --- AC-1: topic resolution -------------------------------------------------

    @Test
    void topicFor_mapsEachEventTypeToItsPreservedTopic() {
        assertThat(PromotionOutboxPublisher.topicFor("CouponUsed")).isEqualTo("promotion.coupon.used");
        assertThat(PromotionOutboxPublisher.topicFor("CouponExpired")).isEqualTo("promotion.coupon.expired");
    }

    @Test
    void topicFor_rejectsUnmappedEventTypes() {
        assertThatThrownBy(() -> PromotionOutboxPublisher.topicFor(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PromotionOutboxPublisher.topicFor("OrderPlaced"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PromotionOutboxPublisher.topicFor("coupon.used"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- AC-2 / AC-3: real publish round-trip -----------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_sendsToMappedTopicWithHeaders_preservesKeyAndValue_thenMarksPublished() {
        PromotionOutboxRepository repository = mock(PromotionOutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        String payload = "{\"event_id\":\"" + eventId + "\",\"event_type\":\"CouponUsed\"}";
        PromotionOutboxEntity row = new PromotionOutboxEntity(
                eventId, "CouponUsed", "Coupon", "coupon-1",
                null, payload, OCCURRED);

        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));
        given(repository.findById(eventId)).willReturn(Optional.of(row));
        given(repository.countByPublishedAtIsNull()).willReturn(0L);
        given(kafka.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        PromotionOutboxPublisher publisher = new PromotionOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertThat(sent.topic()).isEqualTo("promotion.coupon.used");
        // partition_key null → key falls back to aggregateId (couponId) — preserves v1 key
        assertThat(sent.key()).isEqualTo("coupon-1");
        // value byte-identical to the stored envelope payload
        assertThat(sent.value()).isEqualTo(payload);
        assertThat(new String(sent.headers().lastHeader("eventId").value())).isEqualTo(eventId.toString());
        assertThat(new String(sent.headers().lastHeader("eventType").value())).isEqualTo("CouponUsed");

        // mark-published
        assertThat(row.getPublishedAt()).isEqualTo(CLOCK.instant());
        verify(repository).save(row);

        // promotion-prefixed metrics (lag = publish - occurred = 2s)
        assertThat(registry.find("promotion.outbox.publish.success.total")
                .tag("event_type", "CouponUsed").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("promotion.outbox.lag.seconds").timer().count()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void constructor_registersPreservedPendingCountGauge() {
        PromotionOutboxRepository repository = mock(PromotionOutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        given(repository.countByPublishedAtIsNull()).willReturn(4L);

        new PromotionOutboxPublisher(repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        io.micrometer.core.instrument.Gauge gauge =
                registry.find("promotion.outbox.pending.count").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(4.0d);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPending_kafkaFailure_leavesRowPending_recordsFailure_andBacksOff() {
        PromotionOutboxRepository repository = mock(PromotionOutboxRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        UUID eventId = UUID.randomUUID();
        PromotionOutboxEntity row = new PromotionOutboxEntity(
                eventId, "CouponExpired", "Coupon", "coupon-1",
                null, "{}", OCCURRED);
        given(repository.findPending(any(Pageable.class))).willReturn(List.of(row));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafka.send(any(ProducerRecord.class))).willReturn(failed);

        PromotionOutboxPublisher publisher = new PromotionOutboxPublisher(
                repository, kafka, new SyncTransactionTemplate(), CLOCK, registry, 100);

        publisher.publishPending(); // first tick fails → opens backoff window
        publisher.publishPending(); // same instant → suppressed by backoff

        assertThat(row.getPublishedAt()).isNull();
        verify(kafka).send(any(ProducerRecord.class)); // exactly once: second tick suppressed
        verify(repository, never()).save(any());
        assertThat(registry.find("promotion.outbox.publish.failure.total").counter().count()).isEqualTo(1.0d);
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

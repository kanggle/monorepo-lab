package com.example.promotion.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.promotion.application.event.CouponExpiredEvent;
import com.example.promotion.application.event.CouponUsedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for the {@link SpringPromotionEventPublisher} write path
 * (TASK-BE-444, outbox v2). Asserts that each domain event persists a
 * {@code promotion_outbox} row whose wire-relevant fields are preserved exactly:
 * the row {@code event_id} reuses the event envelope {@code event_id}, the
 * payload is the byte-identical {@code writeValueAsString(event)}, the key
 * source ({@code aggregate_id}) is the {@code couponId}, and {@code occurred_at}
 * is parsed from the envelope timestamp.
 */
class SpringPromotionEventPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T10:15:30Z"), ZoneOffset.UTC);

    private final PromotionOutboxRepository repository = mock(PromotionOutboxRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpringPromotionEventPublisher publisher =
            new SpringPromotionEventPublisher(repository, objectMapper);

    @Test
    void publishCouponUsed_persistsV2Row_preservingEnvelopeIdPayloadAndKey() throws Exception {
        CouponUsedEvent event = CouponUsedEvent.of(
                "coupon-1", "promo-1", "user-1", "order-1", 5000L, CLOCK);

        publisher.publishCouponUsed(event);

        PromotionOutboxEntity row = capturedRow();
        assertThat(row.getEventId()).isEqualTo(UUID.fromString(event.eventId()));
        assertThat(row.getEventType()).isEqualTo("CouponUsed");
        assertThat(row.getAggregateType()).isEqualTo("Coupon");
        assertThat(row.getAggregateId()).isEqualTo("coupon-1");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(Instant.parse(event.occurredAt()));
        // wire-preserving: payload byte-identical to the v1 writeValueAsString(event)
        assertThat(row.getPayload()).isEqualTo(objectMapper.writeValueAsString(event));
        assertThat(row.getPublishedAt()).isNull();
    }

    @Test
    void publishCouponExpired_persistsV2Row_preservingEnvelopeIdPayloadAndKey() throws Exception {
        CouponExpiredEvent event = CouponExpiredEvent.of(
                "coupon-9", "promo-9", "user-9", "tenant-x", CLOCK);

        publisher.publishCouponExpired(event);

        PromotionOutboxEntity row = capturedRow();
        assertThat(row.getEventId()).isEqualTo(UUID.fromString(event.eventId()));
        assertThat(row.getEventType()).isEqualTo("CouponExpired");
        assertThat(row.getAggregateType()).isEqualTo("Coupon");
        assertThat(row.getAggregateId()).isEqualTo("coupon-9");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(Instant.parse(event.occurredAt()));
        assertThat(row.getPayload()).isEqualTo(objectMapper.writeValueAsString(event));
        assertThat(row.getPublishedAt()).isNull();
    }

    private PromotionOutboxEntity capturedRow() {
        ArgumentCaptor<PromotionOutboxEntity> captor = ArgumentCaptor.forClass(PromotionOutboxEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}

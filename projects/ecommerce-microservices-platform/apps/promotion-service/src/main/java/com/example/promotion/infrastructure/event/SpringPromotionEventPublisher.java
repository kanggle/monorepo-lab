package com.example.promotion.infrastructure.event;

import com.example.promotion.application.event.CouponExpiredEvent;
import com.example.promotion.application.event.CouponUsedEvent;
import com.example.promotion.application.port.PromotionEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * promotion-service outbox write path (TASK-BE-444, outbox v2).
 *
 * <p>Persists one {@link PromotionOutboxEntity} ({@code promotion_outbox} table)
 * per domain event inside the caller's transaction, so the business mutation and
 * the outbox row commit atomically. The {@link PromotionOutboxPublisher} relay
 * drains the table to Kafka.
 *
 * <p>Replaces the v1 lib {@code OutboxWriter} ({@code OutboxJpaEntity},
 * server-assigned {@code BIGSERIAL}, {@code status} string). Wire is preserved
 * exactly:
 * <ul>
 *   <li>{@code payload} = {@code objectMapper.writeValueAsString(event)} — the
 *       full event envelope JSON, byte-identical to v1.</li>
 *   <li>{@code aggregate_type}/{@code aggregate_id}/{@code eventType} =
 *       {@code "Coupon"}/{@code couponId}/the literal event-type name, exactly as
 *       the v1 {@code outboxWriter.save("Coupon", couponId, "CouponUsed", …)}
 *       call. {@code aggregate_id} becomes the Kafka record key (per-coupon
 *       ordering) since {@code partition_key} is null.</li>
 *   <li>{@code event_id} reuses the event's own envelope {@code event_id} so the
 *       Kafka header {@code eventId} matches the payload {@code event_id} — no
 *       second identifier, no payload mutation.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringPromotionEventPublisher implements PromotionEventPublisher {

    private static final String AGGREGATE_TYPE = "Coupon";
    private static final String EVENT_TYPE_COUPON_USED = "CouponUsed";
    private static final String EVENT_TYPE_COUPON_EXPIRED = "CouponExpired";

    private final PromotionOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publishCouponUsed(CouponUsedEvent event) {
        outboxRepository.save(toRow(
                event.eventId(), EVENT_TYPE_COUPON_USED, event.payload().couponId(),
                event.occurredAt(), serialize(event)));
    }

    @Override
    public void publishCouponExpired(CouponExpiredEvent event) {
        outboxRepository.save(toRow(
                event.eventId(), EVENT_TYPE_COUPON_EXPIRED, event.payload().couponId(),
                event.occurredAt(), serialize(event)));
    }

    private PromotionOutboxEntity toRow(String eventId, String eventType, String couponId,
                                        String occurredAt, String payload) {
        return new PromotionOutboxEntity(
                UUID.fromString(eventId),
                eventType,
                AGGREGATE_TYPE,
                couponId,
                null, // partition_key: publisher falls back to aggregateId (couponId)
                payload,
                Instant.parse(occurredAt));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }
}

package com.example.promotion.application.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record CouponUsedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        Payload payload
) {
    public static CouponUsedEvent of(String couponId, String promotionId, String userId,
                                      String orderId, long discountAmount, Clock clock) {
        return new CouponUsedEvent(
                UUID.randomUUID().toString(),
                "CouponUsed",
                Instant.now(clock).toString(),
                "promotion-service",
                new Payload(couponId, promotionId, userId, orderId, discountAmount,
                        Instant.now(clock).toString())
        );
    }

    public record Payload(
            String couponId,
            String promotionId,
            String userId,
            String orderId,
            long discountAmount,
            String usedAt
    ) {
    }
}

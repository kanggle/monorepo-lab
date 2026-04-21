package com.example.promotion.application.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record CouponExpiredEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        Payload payload
) {
    public static CouponExpiredEvent of(String couponId, String promotionId,
                                         String userId, Clock clock) {
        return new CouponExpiredEvent(
                UUID.randomUUID().toString(),
                "CouponExpired",
                Instant.now(clock).toString(),
                "promotion-service",
                new Payload(couponId, promotionId, userId, Instant.now(clock).toString())
        );
    }

    public record Payload(
            String couponId,
            String promotionId,
            String userId,
            String expiredAt
    ) {
    }
}

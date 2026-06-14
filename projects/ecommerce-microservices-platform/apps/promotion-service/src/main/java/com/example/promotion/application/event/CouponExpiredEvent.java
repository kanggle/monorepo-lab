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
        @JsonProperty("tenant_id") String tenantId,
        Payload payload
) {
    public static CouponExpiredEvent of(String couponId, String promotionId,
                                         String userId, String tenantId, Clock clock) {
        // Envelope tenant (M5). The expiry sweep is a global/tenant-agnostic
        // operational batch, but each expired coupon belongs to a tenant — the
        // caller passes the expiring coupon's own row tenant_id so the event stays
        // within that coupon's tenant boundary. Null → default tenant (net-zero, D8).
        return new CouponExpiredEvent(
                UUID.randomUUID().toString(),
                "CouponExpired",
                Instant.now(clock).toString(),
                "promotion-service",
                tenantId,
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

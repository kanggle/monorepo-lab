package com.example.promotion.application.event;

import com.example.promotion.domain.tenant.TenantContext;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record CouponUsedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        @JsonProperty("tenant_id") String tenantId,
        Payload payload
) {
    public static CouponUsedEvent of(String couponId, String promotionId, String userId,
                                      String orderId, long discountAmount, Clock clock) {
        // Envelope tenant (M5). Coupon apply is request-originated, so the tenant is
        // the request context; unset → default tenant (net-zero, D8).
        return new CouponUsedEvent(
                UUID.randomUUID().toString(),
                "CouponUsed",
                Instant.now(clock).toString(),
                "promotion-service",
                TenantContext.currentTenant(),
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

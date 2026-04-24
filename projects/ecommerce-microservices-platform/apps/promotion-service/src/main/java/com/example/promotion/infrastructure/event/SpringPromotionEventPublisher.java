package com.example.promotion.infrastructure.event;

import com.example.promotion.application.event.CouponExpiredEvent;
import com.example.promotion.application.event.CouponUsedEvent;
import com.example.promotion.application.port.PromotionEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpringPromotionEventPublisher implements PromotionEventPublisher {

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    @Override
    public void publishCouponUsed(CouponUsedEvent event) {
        String payload = serialize(event);
        outboxWriter.save("Coupon", event.payload().couponId(), "CouponUsed", payload);
    }

    @Override
    public void publishCouponExpired(CouponExpiredEvent event) {
        String payload = serialize(event);
        outboxWriter.save("Coupon", event.payload().couponId(), "CouponExpired", payload);
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }
}

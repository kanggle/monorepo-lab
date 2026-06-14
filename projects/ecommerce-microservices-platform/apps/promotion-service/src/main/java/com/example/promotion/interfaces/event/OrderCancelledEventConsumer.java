package com.example.promotion.interfaces.event;

import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.domain.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledEventConsumer {

    private final CouponCommandService couponCommandService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.order.cancelled", groupId = "promotion-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, OrderCancelledEvent.class));
    }

    public void handle(OrderCancelledEvent event) {
        if (event.payload() == null) {
            log.warn("OrderCancelled event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String orderId = event.payload().orderId();
        if (orderId == null || orderId.isBlank()) {
            log.warn("OrderCancelled event missing orderId, skipping. eventId={}", event.eventId());
            return;
        }

        log.info("Processing OrderCancelled event: orderId={}, eventId={}", orderId, event.eventId());
        // Bind the order's tenant from the consumed envelope (M5) so the restore stays
        // within the tenant boundary. The restore reads by globally-unique orderId
        // (tenant-agnostic) and updates preserve each coupon's own tenant_id
        // (updatable=false); a pre-multi-tenant envelope (null tenant) resolves to the
        // default tenant (net-zero, D8). Cleared in finally so the pooled listener
        // thread leaks no context to the next message.
        try {
            TenantContext.set(event.tenantId());
            couponCommandService.restoreCouponsByOrderId(orderId);
        } finally {
            TenantContext.clear();
        }
    }
}

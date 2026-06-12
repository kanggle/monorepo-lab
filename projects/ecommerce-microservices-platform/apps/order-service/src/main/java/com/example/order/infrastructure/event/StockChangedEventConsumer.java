package com.example.order.infrastructure.event;

import com.example.order.application.service.OrderConfirmationService;
import com.example.order.domain.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class StockChangedEventConsumer {

    private final OrderConfirmationService orderConfirmationService;
    private final EventDeduplicationChecker eventDeduplicationChecker;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "product.product.stock-changed", groupId = "order-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        StockChangedEvent event = objectMapper.readValue(payload, StockChangedEvent.class);
        // Bind the order's tenant from the consumed envelope (M5) so the confirm
        // saga and the OrderConfirmed it emits stay within the tenant boundary.
        // A pre-multi-tenant envelope (no tenant_id) resolves to the default tenant
        // (net-zero, D8). Cleared in finally so the pooled listener thread leaks no
        // context to the next message.
        try {
            TenantContext.set(event.tenantId());
            handle(event);
        } finally {
            TenantContext.clear();
        }
    }

    void handle(StockChangedEvent event) {
        if (eventDeduplicationChecker.isDuplicate(event.eventId(), "StockChanged")) {
            return;
        }

        if (event.payload() == null) {
            log.warn("StockChanged event has null payload, skipping");
            return;
        }

        if (!"ORDER_RESERVED".equals(event.payload().reason())) {
            return;
        }

        String orderId = event.payload().orderId();
        if (orderId == null || orderId.isBlank()) {
            log.warn("StockChanged ORDER_RESERVED event has no orderId, skipping. eventId={}", event.eventId());
            return;
        }

        orderConfirmationService.confirmOrder(orderId);
    }
}

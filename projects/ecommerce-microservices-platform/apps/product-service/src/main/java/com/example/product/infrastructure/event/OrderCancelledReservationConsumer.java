package com.example.product.infrastructure.event;

import com.example.product.application.service.ReservationService;
import com.example.product.domain.tenant.TenantContext;
import com.example.product.infrastructure.event.ReservationInboundEvents.OrderCancelledMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code order.order.cancelled} → releases the order's reservation (TASK-BE-428). A
 * previously RESERVED order restores stock + emits {@code StockChanged(ORDER_CANCELLED)} per line;
 * a NEW/BACKORDERED order releases without any stock change (so a later restock does not bind to a
 * cancelled order). Idempotent on an already-released reservation. Dedupe on the envelope
 * {@code event_id}; tenant bound from the envelope for the tenant-scoped stock restore.
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class OrderCancelledReservationConsumer {

    private final ReservationService reservationService;
    private final ReservationEventDedupe dedupe;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "order.order.cancelled", groupId = "product-service-reservation")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, OrderCancelledMessage.class));
    }

    void handle(OrderCancelledMessage event) {
        if (dedupe.isDuplicate(ReservationUuids.parseOrNull(event.eventId()), "order.order.cancelled")) {
            return;
        }
        if (event.payload() == null || event.payload().orderId() == null
                || event.payload().orderId().isBlank()) {
            log.warn("order.order.cancelled has null/blank orderId, skipping. eventId={}", event.eventId());
            return;
        }
        String tenantId = event.tenantId();
        try {
            TenantContext.set(tenantId);
            reservationService.release(event.payload().orderId());
        } finally {
            TenantContext.clear();
        }
    }
}

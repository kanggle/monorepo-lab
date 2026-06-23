package com.example.product.infrastructure.event;

import com.example.product.application.service.ReservationService;
import com.example.product.domain.tenant.TenantContext;
import com.example.product.infrastructure.event.ReservationInboundEvents.PaymentCompletedMessage;
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
 * Consumes {@code payment.payment.completed} → marks the order's reservation as paid
 * (TASK-BE-428). PaymentCompleted carries {@code orderId} only (no line items — see
 * {@code payment-events.md}); the lines come from {@code OrderPlaced}. If the lines have already
 * arrived (placed-first), the reservation reserves immediately; otherwise a payment-first stub is
 * created and reserve fires when {@code OrderPlaced} lands. Dedupe on the envelope {@code event_id}.
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class PaymentCompletedReservationConsumer {

    private final ReservationService reservationService;
    private final ReservationEventDedupe dedupe;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "payment.payment.completed", groupId = "product-service-reservation")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, PaymentCompletedMessage.class));
    }

    void handle(PaymentCompletedMessage event) {
        if (dedupe.isDuplicate(ReservationUuids.parseOrNull(event.eventId()), "payment.payment.completed")) {
            return;
        }
        if (event.payload() == null || event.payload().orderId() == null
                || event.payload().orderId().isBlank()) {
            log.warn("payment.payment.completed has null/blank orderId, skipping. eventId={}", event.eventId());
            return;
        }
        String tenantId = event.tenantId();
        try {
            TenantContext.set(tenantId);
            reservationService.recordPaymentCompleted(event.payload().orderId(), tenantId);
        } finally {
            TenantContext.clear();
        }
    }
}

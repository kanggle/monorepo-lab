package com.example.payment.adapter.in.event;

import com.example.payment.application.service.PaymentRefundService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class OrderCancelledEventConsumer {

    private final PaymentRefundService paymentRefundService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.order.cancelled", groupId = "payment-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, OrderCancelledEvent.class));
    }

    void handle(OrderCancelledEvent event) {
        if (event.payload() == null) {
            log.warn("OrderCancelled event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String orderId = event.payload().orderId();
        if (orderId == null || orderId.isBlank()) {
            log.warn("OrderCancelled event missing orderId, skipping. eventId={}", event.eventId());
            return;
        }

        paymentRefundService.refundPayment(orderId);
    }
}

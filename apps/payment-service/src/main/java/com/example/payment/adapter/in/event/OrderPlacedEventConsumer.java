package com.example.payment.adapter.in.event;

import com.example.payment.application.service.PaymentProcessingService;
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
public class OrderPlacedEventConsumer {

    private final PaymentProcessingService paymentProcessingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.order.placed", groupId = "payment-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, OrderPlacedEvent.class));
    }

    void handle(OrderPlacedEvent event) {
        if (event.payload() == null) {
            log.warn("OrderPlaced event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String orderId = event.payload().orderId();
        String userId = event.payload().userId();
        long amount = event.payload().totalPrice();

        if (orderId == null || userId == null) {
            log.warn("OrderPlaced event missing required fields, skipping. eventId={}", event.eventId());
            return;
        }

        if (amount <= 0) {
            log.warn("OrderPlaced event has invalid amount={}, skipping. eventId={}", amount, event.eventId());
            return;
        }

        paymentProcessingService.processPayment(orderId, userId, amount);
    }
}

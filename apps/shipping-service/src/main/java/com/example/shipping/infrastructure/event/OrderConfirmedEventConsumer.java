package com.example.shipping.infrastructure.event;

import com.example.shipping.application.command.CreateShippingCommand;
import com.example.shipping.application.service.ShippingCommandService;
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
@RequiredArgsConstructor
public class OrderConfirmedEventConsumer {

    private final ShippingCommandService shippingCommandService;
    private final EventDeduplicationChecker eventDeduplicationChecker;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "order.order.confirmed", groupId = "shipping-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, OrderConfirmedEvent.class));
    }

    void handle(OrderConfirmedEvent event) {
        if (eventDeduplicationChecker.isDuplicate(event.eventId(), "OrderConfirmed")) {
            return;
        }

        if (event.payload() == null) {
            log.warn("OrderConfirmed event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String orderId = event.payload().orderId();
        if (orderId == null || orderId.isBlank()) {
            log.warn("OrderConfirmed event has no orderId, skipping. eventId={}", event.eventId());
            return;
        }

        String userId = event.payload().userId();
        if (userId == null || userId.isBlank()) {
            log.warn("OrderConfirmed event has no userId, skipping. eventId={}", event.eventId());
            return;
        }

        shippingCommandService.createShipping(new CreateShippingCommand(orderId, userId));
    }
}

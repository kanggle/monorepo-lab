package com.example.order.infrastructure.event;

import com.example.order.application.service.OrderShippingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Return-leg tail of the storefront→warehouse loop (ADR-MONO-022 §D7): consumes
 * the ecommerce-internal {@code ShippingStatusChanged} event and, when the new
 * status is {@code SHIPPED}, flips the Order to {@code SHIPPED}.
 *
 * <p>Other shipping transitions (IN_TRANSIT, DELIVERED) are ignored here — only
 * the SHIPPED edge participates in the order lifecycle today.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class ShippingStatusChangedEventConsumer {

    private final OrderShippingService orderShippingService;
    private final EventDeduplicationChecker eventDeduplicationChecker;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "shipping.shipping.status-changed", groupId = "order-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, ShippingStatusChangedEvent.class));
    }

    void handle(ShippingStatusChangedEvent event) {
        if (eventDeduplicationChecker.isDuplicate(event.eventId(), "ShippingStatusChanged")) {
            return;
        }

        if (event.payload() == null) {
            log.warn("ShippingStatusChanged event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        if (!"SHIPPED".equals(event.payload().newStatus())) {
            return;
        }

        if (EventFieldParser.isBlank(event.payload().orderId())) {
            log.warn("ShippingStatusChanged event has no orderId, skipping. eventId={}", event.eventId());
            return;
        }

        orderShippingService.markShipped(event.payload().orderId());
    }
}

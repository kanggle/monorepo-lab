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
 * the ecommerce-internal {@code ShippingStatusChanged} event and flips the Order
 * along the fulfillment edges — {@code SHIPPED} → Order {@code SHIPPED}, and
 * {@code DELIVERED} → Order {@code SHIPPED → DELIVERED} (TASK-BE-429), completing
 * the order lifecycle.
 *
 * <p>Intermediate transitions (IN_TRANSIT) are ignored — the order lifecycle has
 * no matching state.
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

        String newStatus = event.payload().newStatus();
        boolean shipped = "SHIPPED".equals(newStatus);
        boolean delivered = "DELIVERED".equals(newStatus);
        if (!shipped && !delivered) {
            return;
        }

        if (EventFieldParser.isBlank(event.payload().orderId())) {
            log.warn("ShippingStatusChanged event has no orderId, skipping. eventId={}", event.eventId());
            return;
        }

        if (shipped) {
            orderShippingService.markShipped(event.payload().orderId());
        } else {
            orderShippingService.markDelivered(event.payload().orderId());
        }
    }
}

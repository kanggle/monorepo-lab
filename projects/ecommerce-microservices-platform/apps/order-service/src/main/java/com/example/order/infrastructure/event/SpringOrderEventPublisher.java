package com.example.order.infrastructure.event;

import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.event.OrderPlacedEvent;
import com.example.order.application.event.OrderSagaRecoveryExhaustedEvent;
import com.example.order.application.port.OrderEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class SpringOrderEventPublisher implements OrderEventPublisher {

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    @Override
    public void publishOrderPlaced(OrderPlacedEvent event) {
        String payload = serialize(event);
        outboxWriter.save("Order", event.payload().orderId(), "OrderPlaced", payload);
    }

    @Override
    public void publishOrderCancelled(OrderCancelledEvent event) {
        String payload = serialize(event);
        outboxWriter.save("Order", event.payload().orderId(), "OrderCancelled", payload);
    }

    @Override
    public void publishOrderSagaRecoveryExhausted(OrderSagaRecoveryExhaustedEvent event) {
        String payload = serialize(event);
        outboxWriter.save("Order", event.payload().orderId(),
                "OrderSagaRecoveryExhausted", payload);
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }
}

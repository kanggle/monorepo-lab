package com.example.order.application.port;

import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.event.OrderConfirmedEvent;
import com.example.order.application.event.OrderPlacedEvent;
import com.example.order.application.event.OrderSagaRecoveryExhaustedEvent;

public interface OrderEventPublisher {

    void publishOrderPlaced(OrderPlacedEvent event);

    void publishOrderConfirmed(OrderConfirmedEvent event);

    void publishOrderCancelled(OrderCancelledEvent event);

    void publishOrderSagaRecoveryExhausted(OrderSagaRecoveryExhaustedEvent event);
}

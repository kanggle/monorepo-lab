package com.example.order.application.port;

import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.event.OrderPlacedEvent;

public interface OrderEventPublisher {

    void publishOrderPlaced(OrderPlacedEvent event);

    void publishOrderCancelled(OrderCancelledEvent event);
}

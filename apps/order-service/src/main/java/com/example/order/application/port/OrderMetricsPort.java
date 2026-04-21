package com.example.order.application.port;

public interface OrderMetricsPort {

    void recordOrderPlaced();

    void recordOrderConfirmed();

    void recordOrderCancelled(String reason);

    void recordStatusTransition(String from, String to);

    void recordOrderAmount(long amount);

    void recordEventPublishFailure(String eventType);
}

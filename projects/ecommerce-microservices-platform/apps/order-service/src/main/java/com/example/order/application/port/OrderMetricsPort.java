package com.example.order.application.port;

public interface OrderMetricsPort {

    void recordOrderPlaced();

    void recordOrderConfirmed();

    void recordOrderBackordered();

    void recordOrderCancelled(String reason);

    void recordStatusTransition(String from, String to);

    void recordOrderAmount(long amount);

    void recordEventPublishFailure(String eventType);

    void recordStuckDetectorRun();

    void recordStuckDetectorRecoveryFired(String fromState);

    void recordStuckDetectorExhausted(String fromState);
}

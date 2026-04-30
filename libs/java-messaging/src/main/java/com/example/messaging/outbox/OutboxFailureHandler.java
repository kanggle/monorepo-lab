package com.example.messaging.outbox;

@FunctionalInterface
public interface OutboxFailureHandler {
    void onFailure(String eventType, String aggregateId, Exception e);
}

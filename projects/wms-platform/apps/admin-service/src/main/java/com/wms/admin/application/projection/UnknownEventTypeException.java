package com.wms.admin.application.projection;

/**
 * Thrown by projection services when an envelope's {@code eventType} does not
 * match any known type for the consumer's source service. Non-retryable —
 * Spring Kafka routes the record straight to DLT (idempotency.md § 2.7).
 */
public class UnknownEventTypeException extends IllegalArgumentException {

    public UnknownEventTypeException(String eventType, String topic) {
        super("Unknown eventType=" + eventType + " on topic=" + topic);
    }
}

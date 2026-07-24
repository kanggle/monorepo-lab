package com.example.scmplatform.logistics.adapter.inbound.messaging;

/**
 * Marker exception for a <b>non-retryable</b> consumer failure — a malformed envelope
 * (null {@code eventId} / null {@code payload} / null {@code shipmentId}) that can never
 * succeed on redelivery. The {@code DefaultErrorHandler} is configured to treat this as
 * non-retryable (via {@code addNotRetryableExceptions}), so the record is routed to the
 * DLT <b>immediately</b> without exhausting the {@code [1s,2s,4s]} backoff (ops alert +
 * no silent drop; subscriptions contract § Retry + DLT, task Failure Scenario E).
 *
 * <p>Mirrors demand-planning-service's {@code NonRetryableConsumerException} (the sibling
 * wms→scm consumer).
 */
public class NonRetryableConsumerException extends RuntimeException {

    public NonRetryableConsumerException(String message) {
        super(message);
    }

    public NonRetryableConsumerException(String message, Throwable cause) {
        super(message, cause);
    }
}

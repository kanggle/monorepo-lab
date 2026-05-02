package com.example.security.consumer;

/**
 * Thrown when a consumed Kafka event is missing the required {@code tenant_id} field.
 *
 * <p>TASK-BE-248 Phase 2a: events without {@code tenant_id} cannot be processed
 * because all detection rules require per-tenant isolation. The
 * {@code DefaultErrorHandler} treats this as a non-retryable exception and routes
 * the message directly to the DLQ topic ({@code <original-topic>.dlq}).
 *
 * <p>The {@code outbox.dlq.size} Micrometer counter is incremented (with tag
 * {@code reason=tenant_id_missing}) in {@code KafkaConsumerConfig}'s recoverer
 * callback so that alerts fire on DLQ accumulation.
 */
public class MissingTenantIdException extends RuntimeException {

    private final String eventId;
    private final String eventType;

    public MissingTenantIdException(String eventId, String eventType) {
        super("Event missing tenant_id: eventId=" + eventId + ", eventType=" + eventType);
        this.eventId = eventId;
        this.eventType = eventType;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }
}

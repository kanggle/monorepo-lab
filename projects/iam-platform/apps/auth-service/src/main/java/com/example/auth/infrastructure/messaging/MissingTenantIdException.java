package com.example.auth.infrastructure.messaging;

/**
 * TASK-BE-601 — an inbound event without {@code tenantId}. Same rule as security-service's
 * consumers (TASK-BE-248 Phase 2b): the producer must fix its payload, so the record is
 * routed to the DLQ without retrying.
 */
public class MissingTenantIdException extends RuntimeException {

    public MissingTenantIdException(String eventId, String topic) {
        super("tenantId missing on " + topic + " event " + eventId);
    }
}

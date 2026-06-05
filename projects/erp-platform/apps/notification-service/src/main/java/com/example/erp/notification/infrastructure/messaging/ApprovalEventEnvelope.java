package com.example.erp.notification.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Deserialization DTO for the approval-service event envelope
 * (erp-approval-events.md § Envelope). Consumed verbatim (unchanged producer
 * contract). A breaking envelope change fails to map the required fields and the
 * event routes to DLT (invalid envelope).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApprovalEventEnvelope(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("occurredAt") String occurredAt,
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("source") String source,
        @JsonProperty("aggregateType") String aggregateType,
        @JsonProperty("aggregateId") String aggregateId,
        @JsonProperty("traceId") String traceId,
        @JsonProperty("payload") Map<String, Object> payload
) {

    /**
     * Valid only when it carries the dedupe key ({@code eventId}), an
     * {@code aggregateId}, and a {@code payload}. A null on any of these →
     * invalid → immediate DLT (cannot key the dedupe table — Failure Mode 2).
     */
    public boolean isValid() {
        return eventId != null && !eventId.isBlank()
                && aggregateId != null && !aggregateId.isBlank()
                && payload != null;
    }

    public String payloadString(String key) {
        Object v = payload == null ? null : payload.get(key);
        return v == null ? null : v.toString();
    }
}

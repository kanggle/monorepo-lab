package com.example.scmplatform.inventoryvisibility.adapter.inbound.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Deserialization DTO for the wms-platform event envelope.
 * Schema is the authoritative shape from:
 * projects/wms-platform/specs/contracts/events/inventory-events.md § Global Envelope
 * <p>
 * Cross-project contract: if wms changes the envelope field names or types
 * (breaking change), this class will fail to deserialize and the event goes to DLT.
 * The {@link WmsInventoryEventEnvelopeContractTest} detects regressions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("eventVersion") int eventVersion,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("producer") String producer,
        @JsonProperty("aggregateType") String aggregateType,
        @JsonProperty("aggregateId") String aggregateId,
        @JsonProperty("traceId") String traceId,
        @JsonProperty("actorId") String actorId,
        @JsonProperty("payload") Map<String, Object> payload
) {
    /**
     * Validate that required fields for idempotency are present.
     * Edge Case 2: invalid envelope → send to DLT without retry.
     */
    public boolean isValid() {
        return eventId != null
                && eventType != null && !eventType.isBlank()
                && occurredAt != null
                && payload != null;
    }
}

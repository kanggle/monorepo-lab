package com.example.erp.readmodel.adapter.inbound.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Deserialization DTO for the masterdata-service event envelope
 * (erp-masterdata-events.md § Envelope). Read by the read-model consumers; the
 * producer side is consumed verbatim (unchanged contract).
 *
 * <p>Cross-service contract: if masterdata changes the envelope field names or
 * types (breaking change), this class fails to map the required fields and the
 * event routes to DLT (invalid envelope).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MasterEventEnvelope(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("source") String source,
        @JsonProperty("aggregateType") String aggregateType,
        @JsonProperty("aggregateId") String aggregateId,
        @JsonProperty("traceId") String traceId,
        @JsonProperty("payload") Map<String, Object> payload
) {

    /**
     * An envelope is valid for projection only when it carries the dedupe key
     * ({@code eventId}), an {@code aggregateId} (projection PK), and a
     * {@code payload}. A null {@code eventId} or {@code payload} → invalid →
     * immediate DLT (cannot key the dedupe table — architecture.md Failure
     * Mode 2).
     */
    public boolean isValid() {
        return eventId != null && !eventId.isBlank()
                && aggregateId != null && !aggregateId.isBlank()
                && payload != null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> after() {
        Object after = payload == null ? null : payload.get("after");
        return after instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    public String changeKindRaw() {
        Object v = payload == null ? null : payload.get("changeKind");
        return v == null ? null : v.toString();
    }

    /**
     * The domain timestamp to stamp on the projection. Prefers the payload's
     * {@code occurredAt}, falling back to the envelope's.
     */
    public Instant effectiveOccurredAt() {
        if (payload != null) {
            Object v = payload.get("occurredAt");
            if (v != null) {
                try {
                    return Instant.parse(v.toString());
                } catch (RuntimeException ignored) {
                    // fall through to envelope occurredAt
                }
            }
        }
        return occurredAt;
    }
}

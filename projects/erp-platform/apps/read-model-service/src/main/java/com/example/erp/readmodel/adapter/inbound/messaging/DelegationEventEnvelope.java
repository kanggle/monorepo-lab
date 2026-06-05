package com.example.erp.readmodel.adapter.inbound.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Deserialization DTO for the approval-service delegation event envelope
 * (erp-approval-events.md § v2.1/v2.2 — same envelope schema as the approval
 * transition events). Read by the read-model delegation consumer
 * (TASK-ERP-BE-015); the producer side is consumed verbatim (unchanged
 * contract). Both delegation topics ({@code erp.approval.delegated.v1} +
 * {@code erp.approval.delegation.revoked.v1}) share this envelope; the
 * {@code delegated} payload carries the validity window ({@code validFrom}/
 * {@code validTo}), the {@code revoked} payload does not
 * ({@code @JsonInclude(NON_NULL)}).
 *
 * <p>Cross-service contract: if approval-service changes the envelope/payload
 * field names or types (breaking change), the required-field validation here
 * fails and the event routes to the DLT (invalid envelope).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DelegationEventEnvelope(
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
     * ({@code eventId}), an {@code aggregateId} (= grantId, projection PK), a
     * payload that carries a {@code grantId}, and a non-blank {@code payload}. A
     * null {@code eventId}/{@code aggregateId}/{@code payload}/{@code grantId} →
     * invalid → immediate DLT (cannot key the dedupe / projection).
     */
    public boolean isValid() {
        return eventId != null && !eventId.isBlank()
                && aggregateId != null && !aggregateId.isBlank()
                && payload != null
                && payloadString("grantId") != null && !payloadString("grantId").isBlank();
    }

    /** {@code true} when the envelope/payload tenant resolves to the required tenant. */
    public boolean hasTenant(String requiredTenant) {
        String t = tenantId != null ? tenantId : payloadString("tenantId");
        return requiredTenant.equals(t);
    }

    public String payloadString(String key) {
        Object v = payload == null ? null : payload.get(key);
        return v == null ? null : v.toString();
    }

    /** Parses an ISO-8601 instant payload field, returning {@code null} when absent/unparseable. */
    public Instant payloadInstant(String key) {
        Object v = payload == null ? null : payload.get(key);
        if (v == null) {
            return null;
        }
        try {
            return Instant.parse(v.toString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The domain timestamp to stamp on the projection provenance. Prefers the
     * payload's {@code occurredAt}, falling back to the envelope's.
     */
    public Instant effectiveOccurredAt() {
        Instant payloadOccurredAt = payloadInstant("occurredAt");
        return payloadOccurredAt != null ? payloadOccurredAt : occurredAt;
    }
}

package com.example.erp.readmodel.adapter.inbound.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Deserialization DTO for the approval-service event envelope
 * (erp-approval-events.md § Envelope — same envelope schema as masterdata). Read
 * by the read-model approval consumers; the producer side is consumed verbatim
 * (unchanged contract). All four approval topics share this envelope; the
 * payload header is common (approvalRequestId / subjectType / subjectId /
 * approverId / submitterId / occurredAt / actor) with {@code finalizedAt} +
 * {@code reason} ABSENT-or-present per event ({@code @JsonInclude(NON_NULL)}).
 *
 * <p>Cross-service contract: if approval-service changes the envelope/payload
 * field names or types (breaking change), the required-field validation here
 * fails and the event routes to the DLT (invalid envelope).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApprovalEventEnvelope(
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
     * ({@code eventId}), an {@code aggregateId} (= approvalRequestId, projection
     * PK), and a {@code payload}. A null {@code eventId} or {@code payload} →
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

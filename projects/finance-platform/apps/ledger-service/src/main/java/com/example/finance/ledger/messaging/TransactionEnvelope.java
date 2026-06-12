package com.example.finance.ledger.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Deserialization DTO for the account-service transaction event envelope
 * (libs/java-messaging {@code BaseEventPublisher} shape — finance-account-events.md
 * § Envelope). The wrapper carries {@code eventId} / {@code eventType} /
 * {@code occurredAt} / {@code tenantId} / {@code source} / {@code payload}; the
 * ledger reads {@code eventId} (dedupe key) and the {@code payload} (transaction
 * fields). Tolerant of unknown fields ({@code @JsonIgnoreProperties}).
 *
 * <p>Cross-service contract: if account-service changes the envelope/payload
 * field names (breaking change), the required-field validation here fails and the
 * event routes to the DLT.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionEnvelope(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("source") String source,
        @JsonProperty("aggregateType") String aggregateType,
        @JsonProperty("aggregateId") String aggregateId,
        @JsonProperty("traceId") String traceId,
        @JsonProperty("payload") Map<String, Object> payload) {

    /**
     * An envelope is valid for posting only when it carries the dedupe key
     * ({@code eventId}) and a {@code payload}. A null/blank {@code eventId} or a
     * null {@code payload} → invalid → immediate DLT (cannot key the dedupe
     * table).
     */
    public boolean isValid() {
        return eventId != null && !eventId.isBlank() && payload != null;
    }

    public String payloadString(String key) {
        Object v = payload == null ? null : payload.get(key);
        return v == null ? null : v.toString();
    }

    /** The {@code tenantId} the entry is posted under (envelope first, default finance). */
    public String effectiveTenantId() {
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        return "finance";
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> moneyMap() {
        Object v = payload == null ? null : payload.get("money");
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }
}

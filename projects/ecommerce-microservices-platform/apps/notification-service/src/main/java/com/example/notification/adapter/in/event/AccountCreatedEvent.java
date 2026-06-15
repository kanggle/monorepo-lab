package com.example.notification.adapter.in.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * IAM {@code account.created} lifecycle event (ADR-MONO-037 P1). notification-service
 * consumes it to send the onboarding WELCOME, replacing the dead-topic
 * {@code auth.user.signed-up} (TASK-BE-132).
 *
 * <p>The payload is PII-masked ({@code emailHash} only, no raw email/name), so the
 * WELCOME is sent without personalization vars (ADR-MONO-037 P1). Tolerant
 * deserialization (camelCase primary + snake_case alias + ignore-unknown).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountCreatedEvent(
        @JsonProperty("eventId") @JsonAlias("event_id") String eventId,
        @JsonProperty("eventType") @JsonAlias("event_type") String eventType,
        @JsonProperty("occurredAt") @JsonAlias("occurred_at") String occurredAt,
        String source,
        @JsonProperty("tenantId") @JsonAlias("tenant_id") String tenantId,
        Payload payload
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            @JsonProperty("accountId") @JsonAlias("account_id") String accountId,
            @JsonProperty("tenantId") @JsonAlias("tenant_id") String tenantId,
            String emailHash,
            String status,
            String locale
    ) {}
}

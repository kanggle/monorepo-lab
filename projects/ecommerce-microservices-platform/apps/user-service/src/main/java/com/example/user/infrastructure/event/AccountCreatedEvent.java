package com.example.user.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * IAM {@code account.created} lifecycle event (ADR-MONO-037 P1, aligning to the IAM
 * account-events contract). The authoritative producer is the IAM account-service
 * ({@code AccountOutboxPollingScheduler}); ecommerce consumes it for onboarding,
 * replacing the decommissioned {@code auth.user.signed-up} (TASK-BE-132).
 *
 * <p>The payload is deliberately PII-masked — it carries only {@code accountId}
 * (the canonical subject = OIDC {@code sub} = ecommerce {@code profile.userId}) and a
 * masked {@code emailHash}, never a raw email or name. So onboarding creates a minimal
 * profile and sources real PII from the OIDC token / profile-update later
 * (ADR-MONO-037 P1). The tenant is carried in the payload ({@code tenantId}).
 *
 * <p>Tolerant deserialization (camelCase primary + snake_case alias + ignore-unknown)
 * for forward-compatibility with additive envelope/payload fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountCreatedEvent(
        @JsonProperty("eventId") @JsonAlias("event_id") UUID eventId,
        @JsonProperty("eventType") @JsonAlias("event_type") String eventType,
        @JsonProperty("occurredAt") @JsonAlias("occurred_at") Instant occurredAt,
        String source,
        @JsonProperty("tenantId") @JsonAlias("tenant_id") String tenantId,
        Payload payload
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID accountId,
            @JsonProperty("tenantId") @JsonAlias("tenant_id") String tenantId,
            String emailHash,
            String status,
            String locale,
            @JsonProperty("createdAt") @JsonAlias("created_at") Instant createdAt
    ) {}
}

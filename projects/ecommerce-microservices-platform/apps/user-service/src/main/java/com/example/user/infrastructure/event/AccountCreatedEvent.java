package com.example.user.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * IAM {@code account.created} lifecycle event (ADR-MONO-037 P1, aligning to the IAM
 * account-events contract). The authoritative producer is the IAM account-service
 * ({@code AccountEventPublisher} → {@code BaseEventPublisher.saveEvent}); ecommerce
 * consumes it for onboarding, replacing the decommissioned {@code auth.user.signed-up}
 * (TASK-BE-132).
 *
 * <p><b>The wire shape is FLAT</b> — the fields are top-level, NOT nested under a
 * {@code payload} object (TASK-BE-422). The IAM producer serializes the payload map
 * directly with NO envelope wrapper ({@code BaseEventPublisher.saveEvent}, relayed
 * verbatim by {@code OutboxPublisher}), matching the authoritative
 * {@code iam-platform/specs/contracts/events/account-events.md} § account.created payload.
 * A nested DTO would silently deserialize the whole thing to a {@code null} payload and
 * no-op every event — the defect this task fixes.
 *
 * <p>The payload is deliberately PII-masked — it carries only {@code accountId}
 * (the canonical subject = OIDC {@code sub} = ecommerce {@code profile.userId}) and a
 * masked {@code emailHash}, never a raw email or name. So onboarding creates a minimal
 * profile and sources real PII from the OIDC token / profile-update later
 * (ADR-MONO-037 P1). The tenant is carried at the top level ({@code tenantId}).
 *
 * <p>{@code accountId} is a {@code UUID} to feed {@code AccountCreatedHandler.handle(UUID)}
 * (= {@code profile.userId}) directly. Tolerant deserialization (camelCase primary +
 * snake_case alias + ignore-unknown) for additive forward-compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountCreatedEvent(
        @JsonProperty("accountId") @JsonAlias("account_id") UUID accountId,
        @JsonProperty("tenantId") @JsonAlias("tenant_id") String tenantId,
        @JsonProperty("emailHash") @JsonAlias("email_hash") String emailHash,
        @JsonProperty("status") String status,
        @JsonProperty("locale") String locale,
        @JsonProperty("createdAt") @JsonAlias("created_at") Instant createdAt
) {}

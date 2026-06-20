package com.example.notification.adapter.in.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * IAM {@code account.created} lifecycle event (ADR-MONO-037 P1). notification-service
 * consumes it to send the onboarding WELCOME, replacing the dead-topic
 * {@code auth.user.signed-up} (TASK-BE-132).
 *
 * <p><b>The wire shape is FLAT</b> — the fields are top-level, NOT nested under a
 * {@code payload} object (TASK-BE-422). The IAM producer serializes the payload map
 * directly with NO envelope wrapper, matching the authoritative
 * {@code iam-platform/specs/contracts/events/account-events.md} § account.created payload.
 * Note there is NO {@code eventId} in the flat account.created payload; the consumer derives
 * a stable dedup key from {@code accountId}. A nested DTO would silently deserialize to a
 * {@code null} payload and no-op every event — the defect this task fixes.
 *
 * <p>The payload is PII-masked ({@code emailHash} only, no raw email/name), so the
 * WELCOME is sent without personalization vars (ADR-MONO-037 P1). {@code accountId} is a
 * {@code String} (= recipient userId). Tolerant deserialization (camelCase primary +
 * snake_case alias + ignore-unknown).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountCreatedEvent(
        @JsonProperty("accountId") @JsonAlias("account_id") String accountId,
        @JsonProperty("tenantId") @JsonAlias("tenant_id") String tenantId,
        @JsonProperty("emailHash") @JsonAlias("email_hash") String emailHash,
        @JsonProperty("status") String status,
        @JsonProperty("locale") String locale,
        @JsonProperty("createdAt") @JsonAlias("created_at") String createdAt
) {}

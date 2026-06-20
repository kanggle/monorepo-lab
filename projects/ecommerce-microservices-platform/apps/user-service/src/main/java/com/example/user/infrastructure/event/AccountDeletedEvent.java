package com.example.user.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * IAM {@code account.deleted} lifecycle event (ADR-MONO-037 P2). Published in two
 * phases on the same topic (IAM account-events contract + consumer-integration-guide
 * § GDPR downstream):
 * <ul>
 *   <li>{@code anonymized=false} — grace entry: logical/status delete (withdraw).</li>
 *   <li>{@code anonymized=true} — post-grace: domain-held PII anonymization
 *       (the standing TASK-BE-258 consumer obligation).</li>
 * </ul>
 * The consumer branches on {@code anonymized}; it does NOT self-schedule on
 * {@code gracePeriodEndsAt} (the producer re-emits at grace end).
 *
 * <p><b>The wire shape is FLAT</b> — the fields are top-level, NOT nested under a
 * {@code payload} object (TASK-BE-422). The IAM producer serializes the payload map
 * directly with NO envelope wrapper, matching the authoritative
 * {@code iam-platform/specs/contracts/events/account-events.md} § account.deleted payload.
 * Note there is NO {@code eventId} in the flat account.deleted payload (only
 * {@code account.locked} carries one). A nested DTO would silently deserialize to a
 * {@code null} payload and no-op every event — the defect this task fixes.
 *
 * <p>{@code accountId} is a {@code UUID} to feed {@code UserProfileService} directly.
 * Tolerant deserialization (camelCase primary + snake_case alias + ignore-unknown).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountDeletedEvent(
        @JsonProperty("accountId") @JsonAlias("account_id") UUID accountId,
        @JsonProperty("tenantId") @JsonAlias("tenant_id") String tenantId,
        @JsonProperty("reasonCode") @JsonAlias("reason_code") String reasonCode,
        @JsonProperty("actorType") @JsonAlias("actor_type") String actorType,
        @JsonProperty("actorId") @JsonAlias("actor_id") String actorId,
        @JsonProperty("deletedAt") @JsonAlias("deleted_at") Instant deletedAt,
        @JsonProperty("gracePeriodEndsAt") @JsonAlias("grace_period_ends_at") Instant gracePeriodEndsAt,
        @JsonProperty("anonymized") Boolean anonymized
) {}

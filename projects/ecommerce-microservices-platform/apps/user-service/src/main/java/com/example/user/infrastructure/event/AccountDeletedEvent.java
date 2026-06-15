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
 * <p>Tolerant deserialization (camelCase primary + snake_case alias + ignore-unknown).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountDeletedEvent(
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
            @JsonProperty("reasonCode") @JsonAlias("reason_code") String reasonCode,
            @JsonProperty("actorType") @JsonAlias("actor_type") String actorType,
            @JsonProperty("actorId") @JsonAlias("actor_id") String actorId,
            @JsonProperty("deletedAt") @JsonAlias("deleted_at") Instant deletedAt,
            @JsonProperty("gracePeriodEndsAt") @JsonAlias("grace_period_ends_at") Instant gracePeriodEndsAt,
            Boolean anonymized
    ) {}
}

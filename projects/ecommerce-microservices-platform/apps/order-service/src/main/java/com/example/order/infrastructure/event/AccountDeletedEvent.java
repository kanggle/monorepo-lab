package com.example.order.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * IAM {@code account.deleted} lifecycle event (ADR-MONO-037 P3-B), as consumed by
 * order-service for the order-held PII anonymization cascade. Published in two phases
 * on the same topic (IAM account-events contract schema v2 + consumer-integration-guide
 * § GDPR downstream):
 * <ul>
 *   <li>{@code anonymized=false} — grace entry: order-service takes NO action here
 *       (active-order cancellation is already driven by the {@code user.user.withdrawn}
 *       reaction). This consumer's grace branch is a no-op.</li>
 *   <li>{@code anonymized=true} — post-grace: anonymize the shipping-address PII
 *       snapshot on every order for the subject (the standing TASK-BE-258 obligation
 *       for the order store).</li>
 * </ul>
 * The consumer branches on {@code anonymized}; it does NOT self-schedule on
 * {@code gracePeriodEndsAt} (the IAM producer re-emits at grace end).
 *
 * <p>Tolerant deserialization (camelCase primary + snake_case alias + ignore-unknown),
 * mirroring the user-service {@code AccountDeletedEvent} for forward-compatibility.
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

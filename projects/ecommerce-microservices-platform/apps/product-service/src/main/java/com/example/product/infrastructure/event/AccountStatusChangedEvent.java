package com.example.product.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * IAM {@code account.status.changed} lifecycle event (ADR-MONO-042 D4-C). IAM emits this on
 * EVERY account status transition; product-service's reverse projection (TASK-BE-421) acts
 * ONLY on {@code currentStatus == "LOCKED"} to suspend the backing marketplace seller.
 *
 * <p>This closes the lifecycle hole where a fraud/admin-locked seller stays {@code ACTIVE} in
 * the marketplace. It is the reverse of the forward operator-suspend → IAM-lock leg (TASK-BE-402,
 * {@code product-to-account.md} §3): a forward suspend re-emits {@code LOCKED}, which loops back
 * here and is an already-SUSPENDED idempotent no-op (logged DEBUG, not WARN).
 *
 * <p><b>The wire shape is FLAT</b> — the fields are top-level, NOT nested under a {@code payload}
 * object. This matches the IAM producer ({@code AccountEventPublisher} → {@code
 * BaseEventPublisher.saveEvent}, which serializes the payload map directly with NO envelope
 * wrapper — verified against {@code OutboxPublisher}, which relays the stored payload verbatim)
 * and the authoritative {@code specs/contracts/events/account-events.md} § account.status.changed
 * payload block. A nested DTO would silently deserialize {@code payload} to {@code null} and
 * no-op every event (the trap that the IAM-internal security-service consumer sidesteps by
 * reading from the JSON root). {@code accountId} is a {@code String} to match the seller's stored
 * {@code account_id} directly for {@code findByAccountId}.
 *
 * <p>Tolerant deserialization (camelCase primary + snake_case alias + ignore-unknown) for
 * additive forward-compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountStatusChangedEvent(
        @JsonProperty("accountId") @JsonAlias("account_id") String accountId,
        @JsonProperty("tenantId") @JsonAlias("tenant_id") String tenantId,
        @JsonProperty("previousStatus") @JsonAlias("previous_status") String previousStatus,
        @JsonProperty("currentStatus") @JsonAlias("current_status") String currentStatus,
        @JsonProperty("reasonCode") @JsonAlias("reason_code") String reasonCode,
        @JsonProperty("actorType") @JsonAlias("actor_type") String actorType,
        @JsonProperty("actorId") @JsonAlias("actor_id") String actorId,
        @JsonProperty("occurredAt") @JsonAlias("occurred_at") Instant occurredAt
) {}

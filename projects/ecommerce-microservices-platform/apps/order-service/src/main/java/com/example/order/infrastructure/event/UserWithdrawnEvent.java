package com.example.order.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring user-service UserWithdrawn contract.
 * See specs/contracts/events/user-events.md
 *
 * <p><b>TASK-BE-533 fix.</b> The real envelope (produced by user-service's
 * {@code KafkaUserProfileEventPublisher}) always carries a {@code tenant_id} field
 * (ADR-MONO-030 Step 4 / TASK-BE-367 M5 — mandatory, never blank), which this record does not
 * declare. Without {@code @JsonIgnoreProperties(ignoreUnknown = true)}, every real message threw
 * {@code UnrecognizedPropertyException} on {@code tenant_id} and was routed straight to
 * {@code user.user.withdrawn.dlq} (it is in {@code KafkaConsumerConfig}'s
 * {@code addNotRetryableExceptions}) — i.e. every withdrawal silently failed to cancel the
 * user's active orders, and {@code event_publish_failure_total} could not see it because the
 * failure was on the consume side, not the publish side. Existing sibling records
 * ({@code PaymentRefundedEvent}, {@code AccountDeletedEvent}) already carry this annotation for
 * exactly this reason; {@code UserWithdrawnEvent} was the straggler. Ignoring unknown fields
 * (rather than adding a {@code tenantId} field this consumer has no use for) matches the
 * "additive fields are forward-compatible" rule in {@code specs/contracts/events/user-events.md}
 * § Consumer Rules.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserWithdrawnEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        UserWithdrawnPayload payload
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserWithdrawnPayload(
            String userId,
            String withdrawnAt
    ) {}
}

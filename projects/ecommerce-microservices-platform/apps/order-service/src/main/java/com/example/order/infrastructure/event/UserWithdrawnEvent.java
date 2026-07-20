package com.example.order.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring user-service UserWithdrawn contract.
 * See specs/contracts/events/user-events.md
 *
 * <p><b>{@code @JsonIgnoreProperties} — consistency + defense-in-depth (TASK-BE-545 correction).</b>
 * The real envelope (produced by {@code KafkaUserProfileEventPublisher}) always carries a
 * {@code tenant_id} field (ADR-MONO-030 Step 4 / TASK-BE-367 M5), which this record does not
 * declare. Sibling inbound records ({@code PaymentRefundedEvent}, {@code AccountDeletedEvent})
 * already carry {@code @JsonIgnoreProperties(ignoreUnknown = true)}; adding it here makes the set
 * uniform and matches the "additive fields are forward-compatible" rule in
 * {@code specs/contracts/events/user-events.md} § Consumer Rules.
 *
 * <p><b>This did NOT fix a production outage — correcting the original TASK-BE-533 claim.</b> The
 * consumer is injected with Spring Boot's auto-configured {@code ObjectMapper}, on which
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} is <em>disabled</em> by default (measured against the running
 * {@code OrderServiceApplication} context: the bean reports the feature {@code false} and accepts
 * the full {@code tenant_id} envelope with or without this annotation). Real {@code UserWithdrawn}
 * messages were therefore never rejected and never routed to {@code user.user.withdrawn.dlq}. The
 * earlier claim that "every real message threw {@code UnrecognizedPropertyException}" came from a
 * unit test using a bare {@code new ObjectMapper()} — which enables strict unknown-field handling —
 * an artifact of the fixture's mapper, not the production path. This annotation changes behavior
 * only under a hypothetical global {@code spring.jackson.deserialization.fail-on-unknown-properties=true};
 * that guard, not an outage repair, is what {@code UserWithdrawnEventDeserializationTest} asserts.
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

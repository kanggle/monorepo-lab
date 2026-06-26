package com.example.payment.application.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Terminal money-safety escalation event (TASK-BE-438, ADR-MONO-005 § 2.3 D3 Category A terminal).
 *
 * <p>Emitted by {@code StrandedRefundReconciler} when a {@code StrandedRefund} the sweeper could
 * <b>not</b> auto-heal reaches its terminal {@code UNRESOLVED} state — either the bounded retry
 * budget is exhausted (F2) or the PG issued a <b>definitive 4xx rejection</b> of the cancel. Unlike
 * {@link PaymentRefundStrandedEvent} (which fires once at stranding time and is auto-resolvable by
 * the sweeper), this event signals that the machine has given up and an <b>operator must act</b> on
 * captured funds it could not reverse. Distinct topic so operator paging/alerting can route the
 * terminal case separately from the transient one (D3 R3 — the paging surface is preserved).
 *
 * <p>Written to the transactional outbox in the reconciler's {@code REQUIRES_NEW} boundary,
 * co-committed with the {@code STRANDED → UNRESOLVED} status transition (F3 — the terminal
 * transition and its escalation are all-or-nothing).
 *
 * <p>Topic {@code payment.alert.refund.unresolved}; consumed by an operator/alert subscriber
 * (out-of-scope for ecommerce v1 — published so a future subscriber consumes without spec drift,
 * same disposition as {@code PaymentRefundStranded} / {@code OrderSagaRecoveryExhausted}).
 */
public record PaymentRefundUnresolvedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        @JsonProperty("source") String source,
        @JsonProperty("tenant_id") String tenantId,
        Payload payload
) {
    public record Payload(
            String paymentId,
            String orderId,
            String paymentKey,
            long amount,
            String reason,
            int attempts,
            String lastError,
            String occurredAt
    ) {}

    /**
     * Builds the terminal escalation event. {@code attempts} and {@code lastError} carry the
     * recovery history so an operator can triage why the auto-reconciliation could not heal the
     * stranding; {@code reason} is the original stranding cause (the failing PG exception kind).
     */
    public static PaymentRefundUnresolvedEvent of(String paymentId, String orderId, String paymentKey,
                                                  long amount, String reason, int attempts,
                                                  String lastError, String tenantId) {
        String now = Instant.now().toString();
        return new PaymentRefundUnresolvedEvent(
                UUID.randomUUID().toString(),
                "PaymentRefundUnresolved",
                now,
                "payment-service",
                tenantId,
                new Payload(paymentId, orderId, paymentKey, amount, reason, attempts, lastError, now)
        );
    }
}

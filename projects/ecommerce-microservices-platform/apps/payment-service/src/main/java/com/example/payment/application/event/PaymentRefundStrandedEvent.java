package com.example.payment.application.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Money-safety escalation event (TASK-BE-437).
 *
 * <p>Published when the synchronous HTTP {@code confirm()} post-capture
 * auto-refund (the TASK-BE-435 belt-and-suspenders guard) captures funds for an
 * order that was concurrently cancelled, then <b>fails to reverse the capture at
 * the PG</b> ({@code PgGatewayUnavailableException} 5xx/circuit-open/timeout or
 * {@code PgConfirmFailedException} 4xx). Without this alert the captured customer
 * funds would be silently stranded — no DLT, no retry, no operator record.
 *
 * <p>Written to the transactional outbox in a {@code REQUIRES_NEW} boundary
 * (via {@code PaymentRefundStrandedRecorder}) so it commits independently of the
 * rolled-back {@code confirm()} transaction. Topic
 * {@code payment.alert.refund.stranded}; consumed by an operator/alert subscriber
 * (out-of-scope for v1 — published so a future subscriber consumes without spec
 * drift, mirroring {@code OrderSagaRecoveryExhausted}).
 *
 * <p>Envelope shape mirrors {@link PaymentCompletedEvent} / {@link PaymentRefundedEvent}
 * (event_id / event_type / occurred_at / source / tenant_id / payload).
 */
public record PaymentRefundStrandedEvent(
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
            String occurredAt
    ) {}

    /**
     * Builds the escalation event. {@code reason} distinguishes the PG failure
     * kind (e.g. the exception simple-name) so a reconciliation/operator can tell
     * a transient 5xx-unavailable (cancel may have actually succeeded — check PG
     * state to avoid a double-refund) from a definitive 4xx-rejected (money is
     * captured and needs intervention).
     */
    public static PaymentRefundStrandedEvent of(String paymentId, String orderId, String paymentKey,
                                                long amount, String reason, String tenantId) {
        String now = Instant.now().toString();
        return new PaymentRefundStrandedEvent(
                UUID.randomUUID().toString(),
                "PaymentRefundStranded",
                now,
                "payment-service",
                tenantId,
                new Payload(paymentId, orderId, paymentKey, amount, reason, now)
        );
    }
}

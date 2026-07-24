package com.example.fanplatform.membership.application;

/**
 * Renew use-case input. The {@code tier} is inherited from the prior membership
 * (not supplied by the caller). {@code paymentId} is the PG payment reference —
 * optional under the mock profile ({@code tok_decline} forces a decline); under
 * the {@code portone} profile it is the client-obtained PortOne paymentId,
 * verified server-side.
 */
public record RenewCommand(
        ActorContext actor,
        String priorMembershipId,
        int planMonths,
        String paymentId,
        String idempotencyKey) {
}

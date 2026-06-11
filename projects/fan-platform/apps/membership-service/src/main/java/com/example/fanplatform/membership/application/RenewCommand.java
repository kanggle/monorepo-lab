package com.example.fanplatform.membership.application;

/**
 * Renew use-case input. The {@code tier} is inherited from the prior membership
 * (not supplied by the caller). {@code paymentToken} is optional (mock); the
 * reserved sentinel {@code tok_decline} forces a PG decline.
 */
public record RenewCommand(
        ActorContext actor,
        String priorMembershipId,
        int planMonths,
        String paymentToken,
        String idempotencyKey) {
}

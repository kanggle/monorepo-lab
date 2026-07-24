package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.domain.membership.MembershipTier;

/**
 * Subscribe use-case input. {@code paymentId} is the PG payment reference —
 * optional under the mock profile (the reserved sentinel {@code tok_decline}
 * forces a decline); under the {@code portone} profile it is the PortOne
 * paymentId the client obtained from the payment window, verified server-side.
 */
public record SubscribeCommand(
        ActorContext actor,
        MembershipTier tier,
        int planMonths,
        String paymentId,
        String idempotencyKey) {
}

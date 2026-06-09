package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.domain.membership.MembershipTier;

/**
 * Subscribe use-case input. {@code paymentToken} is optional (mock); the
 * reserved sentinel {@code tok_decline} forces a PG decline.
 */
public record SubscribeCommand(
        ActorContext actor,
        MembershipTier tier,
        int planMonths,
        String paymentToken,
        String idempotencyKey) {
}

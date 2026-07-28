package com.example.fanplatform.membership.application.billing;

import com.example.fanplatform.membership.application.ActorContext;
import com.example.fanplatform.membership.domain.membership.MembershipTier;

/**
 * Enroll (register) a billing key for auto-renewal of a tier. {@code billingKey}
 * is the opaque value the frontend obtained from
 * {@code PortOne.requestIssueBillingKey(...)} (trusted as-is — ADR-MONO-057 §7,
 * Phase 1 does no server-side issuance verification).
 */
public record EnrollBillingKeyCommand(
        ActorContext actor,
        MembershipTier tier,
        String billingKey) {
}

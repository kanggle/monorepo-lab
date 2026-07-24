package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.domain.membership.MembershipTier;

/**
 * Result of an upgrade-quote preview (TASK-FAN-BE-032). {@code chargeMinor} is what
 * the client must pay via PortOne — the subscribe path re-computes and re-verifies
 * the SAME value ({@link UpgradeQuoter}). {@code supersedesMembershipId} is the
 * members-only membership that would be canceled on the upgrade (null = plain
 * subscribe, no credit).
 */
public record UpgradeQuoteView(
        MembershipTier tier,
        int planMonths,
        long listPriceMinor,
        long creditMinor,
        long chargeMinor,
        String supersedesMembershipId) {
}

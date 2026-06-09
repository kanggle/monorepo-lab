package com.example.fanplatform.membership.domain.access;

import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.membership.status.MembershipStatus;

import java.time.Instant;

/**
 * Pure access-evaluation rules — the tier hierarchy and the read-time windowed
 * "active" check. No framework or persistence imports; fully unit-testable.
 *
 * <p>{@code PREMIUM ⊇ MEMBERS_ONLY}: a PREMIUM subscription grants MEMBERS_ONLY
 * content as well (architecture.md § Tier hierarchy).
 */
public final class AccessPolicy {

    private AccessPolicy() {
    }

    /**
     * Tier hierarchy rule.
     *
     * <ul>
     *   <li>{@code heldTier == PREMIUM} → true for any {@code requiredTier};</li>
     *   <li>{@code heldTier == MEMBERS_ONLY} → true only when
     *       {@code requiredTier == MEMBERS_ONLY}.</li>
     * </ul>
     */
    public static boolean tierGrants(MembershipTier heldTier, MembershipTier requiredTier) {
        if (heldTier == null || requiredTier == null) {
            return false;
        }
        if (heldTier == MembershipTier.PREMIUM) {
            return true;
        }
        // heldTier == MEMBERS_ONLY
        return requiredTier == MembershipTier.MEMBERS_ONLY;
    }

    /**
     * Read-time windowed-active check: the instant {@code now} lies within
     * {@code [validFrom, validTo]} (inclusive). {@code now} should already be
     * truncated to micros by the caller (§15) so boundary comparisons match the
     * DB round-trip.
     */
    public static boolean inWindow(Membership membership, Instant now) {
        Instant from = membership.getValidFrom();
        Instant to = membership.getValidTo();
        return !now.isBefore(from) && !now.isAfter(to);
    }

    /**
     * The full § Access Semantics evaluation for a single membership:
     * {@code status == ACTIVE} AND {@code now ∈ [validFrom, validTo]} AND
     * {@code tierGrants(held, required)}.
     */
    public static boolean grantsAccess(Membership membership, MembershipTier requiredTier, Instant now) {
        return membership.getStatus() == MembershipStatus.ACTIVE
                && inWindow(membership, now)
                && tierGrants(membership.getTier(), requiredTier);
    }
}

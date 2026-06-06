package com.example.membership.domain.subscription.status;

import java.util.Map;
import java.util.Set;

/**
 * Defines allowed subscription status transitions.
 * <p>
 * Allowed:
 * <ul>
 *   <li>NONE → ACTIVE (activate)</li>
 *   <li>ACTIVE → EXPIRED (scheduler)</li>
 *   <li>ACTIVE → CANCELLED (user cancel)</li>
 * </ul>
 * Any other transition throws {@link SubscriptionStateTransitionException}.
 */
public class SubscriptionStatusMachine {

    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> ALLOWED = Map.of(
            SubscriptionStatus.NONE, Set.of(SubscriptionStatus.ACTIVE),
            SubscriptionStatus.ACTIVE, Set.of(SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED),
            SubscriptionStatus.EXPIRED, Set.of(),
            SubscriptionStatus.CANCELLED, Set.of()
    );

    public void transition(SubscriptionStatus from, SubscriptionStatus to) {
        if (from == to) {
            throw new SubscriptionStateTransitionException(from, to);
        }
        Set<SubscriptionStatus> targets = ALLOWED.get(from);
        if (targets == null || !targets.contains(to)) {
            throw new SubscriptionStateTransitionException(from, to);
        }
    }

    public boolean isAllowed(SubscriptionStatus from, SubscriptionStatus to) {
        if (from == to) {
            return false;
        }
        Set<SubscriptionStatus> targets = ALLOWED.get(from);
        return targets != null && targets.contains(to);
    }
}

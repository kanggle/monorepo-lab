package com.example.fanplatform.membership.domain.membership.status;

import java.util.Map;
import java.util.Set;

/**
 * Membership status state machine. Stateless utility — every stored transition
 * flows through {@link #ensureTransitionAllowed} so business logic cannot bypass
 * the rules.
 *
 * <p>Transition matrix (architecture.md § State Machine):
 * <ul>
 *   <li>{@code subscribe} (PG approve) → ACTIVE — the row is created directly in
 *       ACTIVE; modelled here as {@code (none/ACTIVE) → ACTIVE} via the entity
 *       factory, not via this matrix.</li>
 *   <li>ACTIVE → CANCELED — terminal cancel.</li>
 *   <li>CANCELED → CANCELED — <strong>idempotent no-op</strong>, handled by the
 *       use case BEFORE this machine is consulted (NOT an error).</li>
 * </ul>
 *
 * <p>{@link MembershipStatus#CANCELED} is terminal — every transition out of
 * CANCELED is forbidden. Expiry is NOT a stored transition (read-time only), so
 * it never appears here.
 */
public final class MembershipStateMachine {

    private static final Map<MembershipStatus, Set<MembershipStatus>> TRANSITIONS = Map.of(
            MembershipStatus.ACTIVE, Set.of(MembershipStatus.CANCELED),
            MembershipStatus.CANCELED, Set.of()
    );

    private MembershipStateMachine() {
    }

    /**
     * @throws InvalidStateTransitionException when {@code current → target} is
     *         not an allowed stored transition. The idempotent CANCELED →
     *         CANCELED no-op MUST be short-circuited by the caller before
     *         invoking this method.
     */
    public static void ensureTransitionAllowed(MembershipStatus current, MembershipStatus target) {
        Set<MembershipStatus> allowed = TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidStateTransitionException(current, target);
        }
    }

    public static boolean isTransitionAllowed(MembershipStatus current, MembershipStatus target) {
        try {
            ensureTransitionAllowed(current, target);
            return true;
        } catch (InvalidStateTransitionException e) {
            return false;
        }
    }
}

package com.example.account.domain.status;

import java.util.Map;
import java.util.Set;

/**
 * Defines allowed state transitions for account lifecycle.
 * All transitions must go through this class. Direct UPDATE is forbidden.
 */
public class AccountStatusMachine {

    private static final Map<AccountStatus, Map<AccountStatus, Set<StatusChangeReason>>> ALLOWED_TRANSITIONS = Map.of(
            AccountStatus.ACTIVE, Map.of(
                    AccountStatus.LOCKED, Set.of(
                            StatusChangeReason.ADMIN_LOCK,
                            StatusChangeReason.AUTO_DETECT,
                            StatusChangeReason.PASSWORD_FAILURE_THRESHOLD,
                            // TASK-BE-231: provisioning operator lock
                            StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE
                    ),
                    AccountStatus.DORMANT, Set.of(
                            StatusChangeReason.DORMANT_365D
                    ),
                    AccountStatus.DELETED, Set.of(
                            StatusChangeReason.USER_REQUEST,
                            StatusChangeReason.ADMIN_DELETE,
                            StatusChangeReason.REGULATED_DELETION,
                            // TASK-BE-231: provisioning operator delete
                            StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE
                    )
            ),
            AccountStatus.LOCKED, Map.of(
                    AccountStatus.ACTIVE, Set.of(
                            StatusChangeReason.ADMIN_UNLOCK,
                            StatusChangeReason.USER_RECOVERY,
                            // TASK-BE-231: provisioning operator unlock
                            StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE
                    ),
                    AccountStatus.DELETED, Set.of(
                            StatusChangeReason.ADMIN_DELETE,
                            StatusChangeReason.REGULATED_DELETION,
                            // TASK-BE-231: provisioning operator delete
                            StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE
                    )
            ),
            AccountStatus.DORMANT, Map.of(
                    AccountStatus.ACTIVE, Set.of(
                            StatusChangeReason.USER_LOGIN
                    ),
                    AccountStatus.DELETED, Set.of(
                            StatusChangeReason.ADMIN_DELETE,
                            StatusChangeReason.REGULATED_DELETION
                    )
            ),
            AccountStatus.DELETED, Map.of(
                    AccountStatus.ACTIVE, Set.of(
                            StatusChangeReason.WITHIN_GRACE_PERIOD
                    )
            )
    );

    /**
     * Validates and returns the transition if allowed.
     *
     * @throws StateTransitionException if the transition is not allowed
     */
    public StatusTransition transition(AccountStatus current, AccountStatus target, StatusChangeReason reason) {
        if (current == target) {
            // Idempotent: same state is not an error for lock operations
            return new StatusTransition(current, target, reason);
        }

        Map<AccountStatus, Set<StatusChangeReason>> fromTransitions = ALLOWED_TRANSITIONS.get(current);
        if (fromTransitions == null) {
            throw new StateTransitionException(current, target, reason);
        }

        Set<StatusChangeReason> allowedReasons = fromTransitions.get(target);
        if (allowedReasons == null || !allowedReasons.contains(reason)) {
            throw new StateTransitionException(current, target, reason);
        }

        return new StatusTransition(current, target, reason);
    }

    /**
     * Check if a transition is allowed without throwing.
     */
    public boolean isAllowed(AccountStatus current, AccountStatus target, StatusChangeReason reason) {
        if (current == target) {
            return true;
        }
        Map<AccountStatus, Set<StatusChangeReason>> fromTransitions = ALLOWED_TRANSITIONS.get(current);
        if (fromTransitions == null) {
            return false;
        }
        Set<StatusChangeReason> allowedReasons = fromTransitions.get(target);
        return allowedReasons != null && allowedReasons.contains(reason);
    }
}

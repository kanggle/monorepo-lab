package com.example.finance.account.domain.account.status;

import com.example.finance.account.domain.error.DomainErrors.AccountStatusTransitionInvalidException;

import java.util.Map;
import java.util.Set;

/**
 * Account state machine — stateless, pure (rules/traits/transactional.md T4,
 * fintech F: state machine via dedicated module). Every transition flows
 * through {@link #ensureTransitionAllowed} so callers cannot bypass the matrix
 * with a direct setter (the {@code Account} aggregate has no status setter).
 *
 * <p>Transition matrix (architecture.md § Account State Machine):
 * <ul>
 *   <li>PENDING_KYC → ACTIVE (KYC reaches required level), CLOSED</li>
 *   <li>ACTIVE → RESTRICTED, FROZEN, CLOSED</li>
 *   <li>RESTRICTED → ACTIVE, FROZEN, CLOSED</li>
 *   <li>FROZEN → ACTIVE, CLOSED</li>
 * </ul>
 *
 * <p>{@code CLOSED} is terminal — every outbound transition is forbidden.
 * Self-transitions are forbidden so callers cannot silently no-op.
 */
public final class AccountStatusMachine {

    private static final Map<AccountStatus, Set<AccountStatus>> TRANSITIONS = Map.of(
            AccountStatus.PENDING_KYC, Set.of(AccountStatus.ACTIVE, AccountStatus.CLOSED),
            AccountStatus.ACTIVE, Set.of(
                    AccountStatus.RESTRICTED, AccountStatus.FROZEN, AccountStatus.CLOSED),
            AccountStatus.RESTRICTED, Set.of(
                    AccountStatus.ACTIVE, AccountStatus.FROZEN, AccountStatus.CLOSED),
            AccountStatus.FROZEN, Set.of(AccountStatus.ACTIVE, AccountStatus.CLOSED));

    private static final Set<AccountStatus> TERMINAL = Set.of(AccountStatus.CLOSED);

    private AccountStatusMachine() {
    }

    public static void ensureTransitionAllowed(AccountStatus current, AccountStatus target) {
        if (TERMINAL.contains(current) || current == target
                || !TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new AccountStatusTransitionInvalidException(
                    "Invalid account status transition: " + current + " → " + target);
        }
    }

    public static boolean isTransitionAllowed(AccountStatus current, AccountStatus target) {
        try {
            ensureTransitionAllowed(current, target);
            return true;
        } catch (AccountStatusTransitionInvalidException e) {
            return false;
        }
    }
}

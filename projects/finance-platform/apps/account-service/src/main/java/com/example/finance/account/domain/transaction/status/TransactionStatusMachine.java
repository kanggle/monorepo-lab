package com.example.finance.account.domain.transaction.status;

import com.example.finance.account.domain.error.DomainErrors.TransactionStatusTransitionInvalidException;

import java.util.Map;
import java.util.Set;

/**
 * Transaction state machine — stateless, pure (transactional T4, fintech
 * F3). Every transition flows through {@link #ensureTransitionAllowed}; the
 * {@code Transaction} aggregate has no status setter so a settled/completed
 * txn cannot be mutated in place — correction is a NEW reversal txn.
 *
 * <p>Matrix (architecture.md § Transaction State Machine):
 * <pre>
 * REQUESTED  → VALIDATED, FAILED
 * VALIDATED  → AUTHORIZED, FAILED
 * AUTHORIZED → SETTLED, FAILED
 * SETTLED    → COMPLETED
 * COMPLETED  → REVERSED   (recorded on the reversal txn referencing the original)
 * </pre>
 *
 * {@code FAILED} / {@code REVERSED} are terminal. Self-transitions forbidden.
 */
public final class TransactionStatusMachine {

    private static final Map<TransactionStatus, Set<TransactionStatus>> TRANSITIONS = Map.of(
            TransactionStatus.REQUESTED, Set.of(
                    TransactionStatus.VALIDATED, TransactionStatus.FAILED),
            TransactionStatus.VALIDATED, Set.of(
                    TransactionStatus.AUTHORIZED, TransactionStatus.FAILED),
            TransactionStatus.AUTHORIZED, Set.of(
                    TransactionStatus.SETTLED, TransactionStatus.FAILED),
            TransactionStatus.SETTLED, Set.of(TransactionStatus.COMPLETED),
            TransactionStatus.COMPLETED, Set.of(TransactionStatus.REVERSED));

    private static final Set<TransactionStatus> TERMINAL = Set.of(
            TransactionStatus.FAILED, TransactionStatus.REVERSED);

    private TransactionStatusMachine() {
    }

    public static void ensureTransitionAllowed(TransactionStatus current,
                                               TransactionStatus target) {
        if (TERMINAL.contains(current) || current == target
                || !TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new TransactionStatusTransitionInvalidException(
                    "Invalid transaction status transition: " + current + " → " + target);
        }
    }

    public static boolean isTransitionAllowed(TransactionStatus current,
                                              TransactionStatus target) {
        try {
            ensureTransitionAllowed(current, target);
            return true;
        } catch (TransactionStatusTransitionInvalidException e) {
            return false;
        }
    }
}

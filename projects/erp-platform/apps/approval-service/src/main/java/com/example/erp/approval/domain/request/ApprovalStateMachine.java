package com.example.erp.approval.domain.request;

import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalAlreadyFinalizedException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalStatusTransitionInvalidException;

import java.util.Map;

/**
 * Approval request state machine — stateless, pure (erp E3, rules/traits/
 * transactional.md T4). The authoritative transition table; every transition
 * flows through {@link #next(ApprovalStatus, ApprovalCommand)} so no caller can
 * bypass the matrix and there is NO direct {@code status} column UPDATE.
 *
 * <p>Transition table (architecture.md § State Machine):
 * <pre>
 * Current \ Command | submit     | approve    | reject     | withdraw
 * DRAFT             | SUBMITTED  | INVALID    | INVALID    | WITHDRAWN
 * SUBMITTED         | INVALID    | APPROVED   | REJECTED   | WITHDRAWN
 * APPROVED ★        | FINALIZED  | FINALIZED  | FINALIZED  | FINALIZED
 * REJECTED ★        | FINALIZED  | FINALIZED  | FINALIZED  | FINALIZED
 * WITHDRAWN ★       | FINALIZED  | FINALIZED  | FINALIZED  | FINALIZED
 * </pre>
 *
 * <p>Guard evaluation order (architecture.md § State Machine cross-cutting
 * guards):
 * <ol>
 *   <li>Finalized guard — current ∈ {APPROVED, REJECTED, WITHDRAWN} → any
 *       command → {@code APPROVAL_ALREADY_FINALIZED} (highest precedence).</li>
 *   <li>Legal-transition guard — the {@code (state, command)} cell is not a
 *       defined edge → {@code APPROVAL_STATUS_TRANSITION_INVALID}.</li>
 * </ol>
 * The route-validity, approver-authorization and reason guards live in the
 * aggregate / application layer (this module owns only the state edges).
 */
public final class ApprovalStateMachine {

    /** Defined edges only — absence of a cell means "illegal transition". */
    private static final Map<ApprovalStatus, Map<ApprovalCommand, ApprovalStatus>> EDGES = Map.of(
            ApprovalStatus.DRAFT, Map.of(
                    ApprovalCommand.SUBMIT, ApprovalStatus.SUBMITTED,
                    ApprovalCommand.WITHDRAW, ApprovalStatus.WITHDRAWN),
            ApprovalStatus.SUBMITTED, Map.of(
                    ApprovalCommand.APPROVE, ApprovalStatus.APPROVED,
                    ApprovalCommand.REJECT, ApprovalStatus.REJECTED,
                    ApprovalCommand.WITHDRAW, ApprovalStatus.WITHDRAWN));

    private ApprovalStateMachine() {
    }

    /**
     * Resolve the next state for {@code (current, command)} or throw. Applies
     * the finalized guard first (highest precedence), then the legal-edge guard.
     *
     * @throws ApprovalAlreadyFinalizedException current is a terminal state
     * @throws ApprovalStatusTransitionInvalidException the cell is not a defined edge
     */
    public static ApprovalStatus next(ApprovalStatus current, ApprovalCommand command) {
        if (current.isFinalized()) {
            throw new ApprovalAlreadyFinalizedException(
                    "request is " + current + " (finalized, immutable) — cannot "
                            + command + "; re-decision requires a new request");
        }
        Map<ApprovalCommand, ApprovalStatus> row = EDGES.get(current);
        ApprovalStatus to = row == null ? null : row.get(command);
        if (to == null) {
            throw new ApprovalStatusTransitionInvalidException(
                    "illegal transition: cannot " + command + " from " + current);
        }
        return to;
    }
}

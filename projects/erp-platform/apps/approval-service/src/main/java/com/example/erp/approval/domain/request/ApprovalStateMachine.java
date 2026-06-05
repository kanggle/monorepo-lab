package com.example.erp.approval.domain.request;

import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalAlreadyFinalizedException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalStatusTransitionInvalidException;

import java.util.Map;

/**
 * Approval request state machine — stateless, pure (erp E3, rules/traits/
 * transactional.md T4). The authoritative transition table; every transition
 * flows through {@link #next(ApprovalStatus, ApprovalCommand, boolean)} so no
 * caller can bypass the matrix and there is NO direct {@code status} column
 * UPDATE.
 *
 * <p>v2.0 (TASK-ERP-BE-012) — the {@code approve} edge is <b>stage-aware</b>:
 * approving from {@code SUBMITTED | IN_REVIEW} yields {@code APPROVED} when the
 * current stage is the LAST of the route, else {@code IN_REVIEW} (the request
 * advances to the next stage). {@code submit} / {@code reject} / {@code withdraw}
 * are stage-independent (the {@code isLastStage} parameter is ignored for them).
 *
 * <p>Transition table (architecture.md § State Machine, v2.0 amendment):
 * <pre>
 * Current \ Command | submit     | approve                      | reject     | withdraw
 * DRAFT             | SUBMITTED  | INVALID                      | INVALID    | WITHDRAWN
 * SUBMITTED         | INVALID    | last? APPROVED : IN_REVIEW    | REJECTED   | WITHDRAWN
 * IN_REVIEW         | INVALID    | last? APPROVED : IN_REVIEW    | REJECTED   | WITHDRAWN
 * APPROVED ★        | FINALIZED  | FINALIZED                    | FINALIZED  | FINALIZED
 * REJECTED ★        | FINALIZED  | FINALIZED                    | FINALIZED  | FINALIZED
 * WITHDRAWN ★       | FINALIZED  | FINALIZED                    | FINALIZED  | FINALIZED
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
 * The route-validity, per-stage approver-authorization and reason guards live in
 * the aggregate / application layer (this module owns only the state edges).
 */
public final class ApprovalStateMachine {

    /**
     * Defined non-approve edges only (stage-independent). The {@code approve}
     * edge from {@code SUBMITTED}/{@code IN_REVIEW} is resolved in {@link #next}
     * because its target depends on {@code isLastStage}.
     */
    private static final Map<ApprovalStatus, Map<ApprovalCommand, ApprovalStatus>> EDGES = Map.of(
            ApprovalStatus.DRAFT, Map.of(
                    ApprovalCommand.SUBMIT, ApprovalStatus.SUBMITTED,
                    ApprovalCommand.WITHDRAW, ApprovalStatus.WITHDRAWN),
            ApprovalStatus.SUBMITTED, Map.of(
                    ApprovalCommand.REJECT, ApprovalStatus.REJECTED,
                    ApprovalCommand.WITHDRAW, ApprovalStatus.WITHDRAWN),
            ApprovalStatus.IN_REVIEW, Map.of(
                    ApprovalCommand.REJECT, ApprovalStatus.REJECTED,
                    ApprovalCommand.WITHDRAW, ApprovalStatus.WITHDRAWN));

    private ApprovalStateMachine() {
    }

    /**
     * Resolve the next state for {@code (current, command, isLastStage)} or
     * throw. Applies the finalized guard first (highest precedence), then the
     * legal-edge guard. The {@code approve} edge from a pending state
     * ({@code SUBMITTED}/{@code IN_REVIEW}) yields {@code APPROVED} when
     * {@code isLastStage} is {@code true}, else {@code IN_REVIEW}.
     * {@code isLastStage} is irrelevant for {@code submit}/{@code reject}/
     * {@code withdraw}.
     *
     * @throws ApprovalAlreadyFinalizedException current is a terminal state
     * @throws ApprovalStatusTransitionInvalidException the cell is not a defined edge
     */
    public static ApprovalStatus next(ApprovalStatus current, ApprovalCommand command,
                                      boolean isLastStage) {
        if (current.isFinalized()) {
            throw new ApprovalAlreadyFinalizedException(
                    "request is " + current + " (finalized, immutable) — cannot "
                            + command + "; re-decision requires a new request");
        }
        if (command == ApprovalCommand.APPROVE
                && (current == ApprovalStatus.SUBMITTED || current == ApprovalStatus.IN_REVIEW)) {
            return isLastStage ? ApprovalStatus.APPROVED : ApprovalStatus.IN_REVIEW;
        }
        Map<ApprovalCommand, ApprovalStatus> row = EDGES.get(current);
        ApprovalStatus to = row == null ? null : row.get(command);
        if (to == null) {
            throw new ApprovalStatusTransitionInvalidException(
                    "illegal transition: cannot " + command + " from " + current);
        }
        return to;
    }

    /**
     * Single-stage (N=1) convenience overload — equivalent to
     * {@code next(current, command, true)}. An approve from a pending state
     * resolves directly to {@code APPROVED} (no {@code IN_REVIEW}), preserving
     * the v1.0 single-stage semantics. Used by the legacy state-machine matrix
     * tests; the aggregate always uses the 3-arg stage-aware form.
     */
    public static ApprovalStatus next(ApprovalStatus current, ApprovalCommand command) {
        return next(current, command, true);
    }
}

package com.example.erp.approval.domain.request;

import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalAlreadyFinalizedException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalStatusTransitionInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full {@code (state, command) → next-state | error} transition matrix
 * (architecture.md § State Machine). Every cell is asserted, including all
 * finalized cells → {@code APPROVAL_ALREADY_FINALIZED}.
 */
class ApprovalStateMachineTest {

    // ---- legal edges ----

    @Test
    @DisplayName("DRAFT + submit → SUBMITTED")
    void draftSubmit() {
        assertThat(ApprovalStateMachine.next(ApprovalStatus.DRAFT, ApprovalCommand.SUBMIT))
                .isEqualTo(ApprovalStatus.SUBMITTED);
    }

    @Test
    @DisplayName("DRAFT + withdraw → WITHDRAWN")
    void draftWithdraw() {
        assertThat(ApprovalStateMachine.next(ApprovalStatus.DRAFT, ApprovalCommand.WITHDRAW))
                .isEqualTo(ApprovalStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("SUBMITTED + approve (single-stage / last) → APPROVED")
    void submittedApprove() {
        assertThat(ApprovalStateMachine.next(ApprovalStatus.SUBMITTED, ApprovalCommand.APPROVE))
                .isEqualTo(ApprovalStatus.APPROVED);
    }

    // ---- v2.0 stage-aware approve edges (TASK-ERP-BE-012) ----

    @Test
    @DisplayName("SUBMITTED + approve, NOT last stage → IN_REVIEW")
    void submittedApproveIntermediate() {
        assertThat(ApprovalStateMachine.next(ApprovalStatus.SUBMITTED, ApprovalCommand.APPROVE, false))
                .isEqualTo(ApprovalStatus.IN_REVIEW);
    }

    @Test
    @DisplayName("SUBMITTED + approve, last stage → APPROVED")
    void submittedApproveLast() {
        assertThat(ApprovalStateMachine.next(ApprovalStatus.SUBMITTED, ApprovalCommand.APPROVE, true))
                .isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("IN_REVIEW + approve, NOT last stage → IN_REVIEW")
    void inReviewApproveIntermediate() {
        assertThat(ApprovalStateMachine.next(ApprovalStatus.IN_REVIEW, ApprovalCommand.APPROVE, false))
                .isEqualTo(ApprovalStatus.IN_REVIEW);
    }

    @Test
    @DisplayName("IN_REVIEW + approve, last stage → APPROVED")
    void inReviewApproveLast() {
        assertThat(ApprovalStateMachine.next(ApprovalStatus.IN_REVIEW, ApprovalCommand.APPROVE, true))
                .isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("IN_REVIEW + reject → REJECTED (stage-independent)")
    void inReviewReject() {
        assertThat(ApprovalStateMachine.next(ApprovalStatus.IN_REVIEW, ApprovalCommand.REJECT, false))
                .isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    @DisplayName("IN_REVIEW + withdraw → WITHDRAWN (stage-independent)")
    void inReviewWithdraw() {
        assertThat(ApprovalStateMachine.next(ApprovalStatus.IN_REVIEW, ApprovalCommand.WITHDRAW, true))
                .isEqualTo(ApprovalStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("IN_REVIEW + submit → TRANSITION_INVALID")
    void inReviewSubmitInvalid() {
        assertThatThrownBy(() ->
                ApprovalStateMachine.next(ApprovalStatus.IN_REVIEW, ApprovalCommand.SUBMIT, true))
                .isInstanceOf(ApprovalStatusTransitionInvalidException.class);
    }

    @Test
    @DisplayName("SUBMITTED + reject → REJECTED")
    void submittedReject() {
        assertThat(ApprovalStateMachine.next(ApprovalStatus.SUBMITTED, ApprovalCommand.REJECT))
                .isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    @DisplayName("SUBMITTED + withdraw → WITHDRAWN")
    void submittedWithdraw() {
        assertThat(ApprovalStateMachine.next(ApprovalStatus.SUBMITTED, ApprovalCommand.WITHDRAW))
                .isEqualTo(ApprovalStatus.WITHDRAWN);
    }

    // ---- illegal (non-terminal predecessor) edges → TRANSITION_INVALID ----

    @Test
    @DisplayName("DRAFT + approve → TRANSITION_INVALID")
    void draftApproveInvalid() {
        assertThatThrownBy(() ->
                ApprovalStateMachine.next(ApprovalStatus.DRAFT, ApprovalCommand.APPROVE))
                .isInstanceOf(ApprovalStatusTransitionInvalidException.class);
    }

    @Test
    @DisplayName("DRAFT + reject → TRANSITION_INVALID")
    void draftRejectInvalid() {
        assertThatThrownBy(() ->
                ApprovalStateMachine.next(ApprovalStatus.DRAFT, ApprovalCommand.REJECT))
                .isInstanceOf(ApprovalStatusTransitionInvalidException.class);
    }

    @Test
    @DisplayName("SUBMITTED + submit → TRANSITION_INVALID")
    void submittedSubmitInvalid() {
        assertThatThrownBy(() ->
                ApprovalStateMachine.next(ApprovalStatus.SUBMITTED, ApprovalCommand.SUBMIT))
                .isInstanceOf(ApprovalStatusTransitionInvalidException.class);
    }

    // ---- finalized cells → ALREADY_FINALIZED (highest precedence) ----

    @Test
    @DisplayName("all finalized states × all commands → ALREADY_FINALIZED")
    void finalizedAllCommands() {
        for (ApprovalStatus terminal : new ApprovalStatus[]{
                ApprovalStatus.APPROVED, ApprovalStatus.REJECTED, ApprovalStatus.WITHDRAWN}) {
            for (ApprovalCommand cmd : ApprovalCommand.values()) {
                assertThatThrownBy(() -> ApprovalStateMachine.next(terminal, cmd))
                        .as("%s + %s", terminal, cmd)
                        .isInstanceOf(ApprovalAlreadyFinalizedException.class);
            }
        }
    }

    @Test
    @DisplayName("isFinalized: terminal states only")
    void isFinalized() {
        assertThat(ApprovalStatus.DRAFT.isFinalized()).isFalse();
        assertThat(ApprovalStatus.SUBMITTED.isFinalized()).isFalse();
        assertThat(ApprovalStatus.IN_REVIEW.isFinalized()).isFalse();
        assertThat(ApprovalStatus.APPROVED.isFinalized()).isTrue();
        assertThat(ApprovalStatus.REJECTED.isFinalized()).isTrue();
        assertThat(ApprovalStatus.WITHDRAWN.isFinalized()).isTrue();
    }
}

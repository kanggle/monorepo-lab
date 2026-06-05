package com.example.erp.approval.domain.request;

import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalAlreadyFinalizedException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalNotAuthorizedApproverException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalStatusTransitionInvalidException;
import com.example.erp.approval.domain.error.ApprovalErrors.ValidationException;
import com.example.erp.approval.domain.route.ApprovalRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aggregate invariant tests — transition methods funnel through the state
 * machine, then apply per-stage approver-eligibility (I4) + reason (E4) guards.
 * Covers single-stage (v1.0 back-compat) + multi-stage (TASK-ERP-BE-012)
 * advancement.
 */
class ApprovalRequestTest {

    private static final Instant NOW = Instant.parse("2026-06-05T00:00:00Z");

    private ApprovalRequest draft(ApprovalRoute route) {
        return ApprovalRequest.createDraft("appr-1", "erp",
                new ApprovalSubject(SubjectType.DEPARTMENT, "dept-1"), "title", null,
                route, "emp-sub", NOW);
    }

    private ApprovalRoute single() {
        return ApprovalRoute.singleStage("emp-sub", "emp-app");
    }

    private ApprovalRoute twoStage() {
        return ApprovalRoute.multiStage("emp-sub", List.of("emp-app1", "emp-app2"));
    }

    // ---- single-stage (v1.0 back-compat) ----

    @Test
    @DisplayName("create → DRAFT, stage 0, totalStages reflects the route")
    void create() {
        ApprovalRequest r = draft(single());
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.DRAFT);
        assertThat(r.getSubmittedAt()).isNull();
        assertThat(r.getFinalizedAt()).isNull();
        assertThat(r.getApproverId()).isEqualTo("emp-app");
        assertThat(r.getSubmitterId()).isEqualTo("emp-sub");
        assertThat(r.getCurrentStageIndex()).isZero();
        assertThat(r.getTotalStages()).isEqualTo(1);
    }

    @Test
    @DisplayName("single-stage submit→approve → APPROVED (no IN_REVIEW)")
    void singleStageHappy() {
        ApprovalRequest r = draft(single());
        ApprovalRoute route = single();
        r.submit(NOW);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.SUBMITTED);
        r.approve("emp-app", route, NOW);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(r.getFinalizedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("approve by a non-approver → APPROVAL_NOT_AUTHORIZED_APPROVER")
    void approveByNonApprover() {
        ApprovalRequest r = draft(single());
        r.submit(NOW);
        assertThatThrownBy(() -> r.approve("emp-other", single(), NOW))
                .isInstanceOf(ApprovalNotAuthorizedApproverException.class);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.SUBMITTED);
    }

    @Test
    @DisplayName("approve on a DRAFT → APPROVAL_STATUS_TRANSITION_INVALID (before authz)")
    void approveOnDraftInvalid() {
        ApprovalRequest r = draft(single());
        assertThatThrownBy(() -> r.approve("emp-app", single(), NOW))
                .isInstanceOf(ApprovalStatusTransitionInvalidException.class);
    }

    @Test
    @DisplayName("reject requires reason")
    void rejectRequiresReason() {
        ApprovalRequest r = draft(single());
        r.submit(NOW);
        assertThatThrownBy(() -> r.reject("emp-app", single(), "  ", NOW))
                .isInstanceOf(ValidationException.class);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.SUBMITTED);
    }

    @Test
    @DisplayName("reject by approver with reason → REJECTED")
    void rejectByApprover() {
        ApprovalRequest r = draft(single());
        r.submit(NOW);
        r.reject("emp-app", single(), "budget", NOW);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    @DisplayName("withdraw by submitter with reason → WITHDRAWN")
    void withdrawBySubmitter() {
        ApprovalRequest r = draft(single());
        r.submit(NOW);
        r.withdraw("emp-sub", "revise", NOW);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("withdraw by non-submitter → APPROVAL_NOT_AUTHORIZED_APPROVER")
    void withdrawByNonSubmitter() {
        ApprovalRequest r = draft(single());
        r.submit(NOW);
        assertThatThrownBy(() -> r.withdraw("emp-app", "x", NOW))
                .isInstanceOf(ApprovalNotAuthorizedApproverException.class);
    }

    @Test
    @DisplayName("any command on a finalized request → APPROVAL_ALREADY_FINALIZED")
    void finalizedReprocess() {
        ApprovalRequest r = draft(single());
        r.submit(NOW);
        r.approve("emp-app", single(), NOW);
        assertThatThrownBy(() -> r.approve("emp-app", single(), NOW))
                .isInstanceOf(ApprovalAlreadyFinalizedException.class);
        assertThatThrownBy(() -> r.reject("emp-app", single(), "x", NOW))
                .isInstanceOf(ApprovalAlreadyFinalizedException.class);
        assertThatThrownBy(() -> r.withdraw("emp-sub", "x", NOW))
                .isInstanceOf(ApprovalAlreadyFinalizedException.class);
    }

    // ---- multi-stage (TASK-ERP-BE-012) ----

    @Test
    @DisplayName("2-stage: stage-0 approve → IN_REVIEW (advance), stage-1 approve → APPROVED")
    void twoStageAdvance() {
        ApprovalRequest r = draft(twoStage());
        ApprovalRoute route = twoStage();
        r.submit(NOW);
        assertThat(r.getTotalStages()).isEqualTo(2);
        assertThat(r.getApproverId()).isEqualTo("emp-app1");

        r.approve("emp-app1", route, NOW);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.IN_REVIEW);
        assertThat(r.getCurrentStageIndex()).isEqualTo(1);
        assertThat(r.getApproverId()).isEqualTo("emp-app2");   // denorm follows the stage
        assertThat(r.getFinalizedAt()).isNull();

        r.approve("emp-app2", route, NOW);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(r.getFinalizedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("per-stage authz: a later stage's approver cannot pre-approve stage 0")
    void laterStageApproverCannotPreApprove() {
        ApprovalRequest r = draft(twoStage());
        ApprovalRoute route = twoStage();
        r.submit(NOW);
        assertThatThrownBy(() -> r.approve("emp-app2", route, NOW))
                .isInstanceOf(ApprovalNotAuthorizedApproverException.class);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.SUBMITTED);
        assertThat(r.getCurrentStageIndex()).isZero();
    }

    @Test
    @DisplayName("per-stage authz: stage-0 approver cannot approve again once advanced to IN_REVIEW")
    void earlierStageApproverCannotReApprove() {
        ApprovalRequest r = draft(twoStage());
        ApprovalRoute route = twoStage();
        r.submit(NOW);
        r.approve("emp-app1", route, NOW);   // now IN_REVIEW at stage 1
        assertThatThrownBy(() -> r.approve("emp-app1", route, NOW))
                .isInstanceOf(ApprovalNotAuthorizedApproverException.class);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.IN_REVIEW);
        assertThat(r.getCurrentStageIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("reject from IN_REVIEW by the current stage approver → REJECTED")
    void rejectFromInReview() {
        ApprovalRequest r = draft(twoStage());
        ApprovalRoute route = twoStage();
        r.submit(NOW);
        r.approve("emp-app1", route, NOW);   // IN_REVIEW at stage 1
        r.reject("emp-app2", route, "no", NOW);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(r.getFinalizedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("withdraw from IN_REVIEW by submitter → WITHDRAWN")
    void withdrawFromInReview() {
        ApprovalRequest r = draft(twoStage());
        ApprovalRoute route = twoStage();
        r.submit(NOW);
        r.approve("emp-app1", route, NOW);   // IN_REVIEW
        r.withdraw("emp-sub", "stop", NOW);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.WITHDRAWN);
    }
}

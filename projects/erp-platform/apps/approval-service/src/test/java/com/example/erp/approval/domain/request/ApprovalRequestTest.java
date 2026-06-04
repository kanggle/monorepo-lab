package com.example.erp.approval.domain.request;

import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalAlreadyFinalizedException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalNotAuthorizedApproverException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalStatusTransitionInvalidException;
import com.example.erp.approval.domain.error.ApprovalErrors.ValidationException;
import com.example.erp.approval.domain.route.ApprovalRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aggregate invariant tests — transition methods funnel through the state
 * machine, then apply approver-eligibility (I4) + reason (E4) guards.
 */
class ApprovalRequestTest {

    private static final Instant NOW = Instant.parse("2026-06-05T00:00:00Z");

    private ApprovalRequest draft() {
        return ApprovalRequest.createDraft("appr-1", "erp",
                new ApprovalSubject(SubjectType.DEPARTMENT, "dept-1"), "title", null,
                ApprovalRoute.singleStage("emp-sub", "emp-app"), "emp-sub", NOW);
    }

    @Test
    @DisplayName("create → DRAFT, no submitted/finalized timestamps")
    void create() {
        ApprovalRequest r = draft();
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.DRAFT);
        assertThat(r.getSubmittedAt()).isNull();
        assertThat(r.getFinalizedAt()).isNull();
        assertThat(r.getApproverId()).isEqualTo("emp-app");
        assertThat(r.getSubmitterId()).isEqualTo("emp-sub");
    }

    @Test
    @DisplayName("submit → SUBMITTED + submittedAt stamped")
    void submit() {
        ApprovalRequest r = draft();
        r.submit(NOW);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.SUBMITTED);
        assertThat(r.getSubmittedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("approve by the route approver → APPROVED")
    void approveByApprover() {
        ApprovalRequest r = draft();
        r.submit(NOW);
        r.approve("emp-app", NOW);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(r.getFinalizedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("approve by a non-approver → APPROVAL_NOT_AUTHORIZED_APPROVER")
    void approveByNonApprover() {
        ApprovalRequest r = draft();
        r.submit(NOW);
        assertThatThrownBy(() -> r.approve("emp-other", NOW))
                .isInstanceOf(ApprovalNotAuthorizedApproverException.class);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.SUBMITTED);
    }

    @Test
    @DisplayName("approve on a DRAFT → APPROVAL_STATUS_TRANSITION_INVALID (before authz)")
    void approveOnDraftInvalid() {
        ApprovalRequest r = draft();
        assertThatThrownBy(() -> r.approve("emp-app", NOW))
                .isInstanceOf(ApprovalStatusTransitionInvalidException.class);
    }

    @Test
    @DisplayName("reject requires reason")
    void rejectRequiresReason() {
        ApprovalRequest r = draft();
        r.submit(NOW);
        assertThatThrownBy(() -> r.reject("emp-app", "  ", NOW))
                .isInstanceOf(ValidationException.class);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.SUBMITTED);
    }

    @Test
    @DisplayName("reject by approver with reason → REJECTED")
    void rejectByApprover() {
        ApprovalRequest r = draft();
        r.submit(NOW);
        r.reject("emp-app", "budget", NOW);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    @DisplayName("withdraw by submitter with reason → WITHDRAWN")
    void withdrawBySubmitter() {
        ApprovalRequest r = draft();
        r.submit(NOW);
        r.withdraw("emp-sub", "revise", NOW);
        assertThat(r.getStatus()).isEqualTo(ApprovalStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("withdraw by non-submitter → APPROVAL_NOT_AUTHORIZED_APPROVER")
    void withdrawByNonSubmitter() {
        ApprovalRequest r = draft();
        r.submit(NOW);
        assertThatThrownBy(() -> r.withdraw("emp-app", "x", NOW))
                .isInstanceOf(ApprovalNotAuthorizedApproverException.class);
    }

    @Test
    @DisplayName("any command on a finalized request → APPROVAL_ALREADY_FINALIZED")
    void finalizedReprocess() {
        ApprovalRequest r = draft();
        r.submit(NOW);
        r.approve("emp-app", NOW);
        assertThatThrownBy(() -> r.approve("emp-app", NOW))
                .isInstanceOf(ApprovalAlreadyFinalizedException.class);
        assertThatThrownBy(() -> r.reject("emp-app", "x", NOW))
                .isInstanceOf(ApprovalAlreadyFinalizedException.class);
        assertThatThrownBy(() -> r.withdraw("emp-sub", "x", NOW))
                .isInstanceOf(ApprovalAlreadyFinalizedException.class);
    }
}

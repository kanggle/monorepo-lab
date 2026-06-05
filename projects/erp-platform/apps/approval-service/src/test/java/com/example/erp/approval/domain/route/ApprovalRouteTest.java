package com.example.erp.approval.domain.route;

import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalRouteInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalRouteTest {

    @Test
    @DisplayName("valid single-stage route: submitter ≠ approver")
    void validRoute() {
        ApprovalRoute route = ApprovalRoute.singleStage("emp-submitter", "emp-approver");
        assertThat(route.stageCount()).isEqualTo(1);
        assertThat(route.approverId()).isEqualTo("emp-approver");
        assertThat(route.approverAt(0).approverId()).isEqualTo("emp-approver");
        assertThat(route.isLastStage(0)).isTrue();
        assertThat(route.isApprover("emp-approver")).isTrue();
        assertThat(route.isApprover("emp-other")).isFalse();
    }

    @Test
    @DisplayName("valid multi-stage route: ordered stages, accessors")
    void validMultiStage() {
        ApprovalRoute route = ApprovalRoute.multiStage("emp-sub", List.of("emp-a", "emp-b", "emp-c"));
        assertThat(route.stageCount()).isEqualTo(3);
        assertThat(route.approverAt(0).approverId()).isEqualTo("emp-a");
        assertThat(route.approverAt(2).approverId()).isEqualTo("emp-c");
        assertThat(route.isLastStage(0)).isFalse();
        assertThat(route.isLastStage(2)).isTrue();
        assertThat(route.isApproverAt(1, "emp-b")).isTrue();
        assertThat(route.isApproverAt(1, "emp-a")).isFalse();
        assertThat(route.approverId()).isEqualTo("emp-a");   // stage 0, back-compat
    }

    @Test
    @DisplayName("empty stage list → APPROVAL_ROUTE_INVALID")
    void emptyStagesRejected() {
        assertThatThrownBy(() -> ApprovalRoute.multiStage("emp-1", List.of()))
                .isInstanceOf(ApprovalRouteInvalidException.class);
    }

    @Test
    @DisplayName("blank approver at any stage → APPROVAL_ROUTE_INVALID")
    void blankStageRejected() {
        assertThatThrownBy(() -> ApprovalRoute.multiStage("emp-1", Arrays.asList("emp-a", "  ")))
                .isInstanceOf(ApprovalRouteInvalidException.class);
    }

    @Test
    @DisplayName("submitter at any stage (self-approval) → APPROVAL_ROUTE_INVALID")
    void selfApprovalAnyStageRejected() {
        assertThatThrownBy(() -> ApprovalRoute.multiStage("emp-1", List.of("emp-a", "emp-1")))
                .isInstanceOf(ApprovalRouteInvalidException.class)
                .hasMessageContaining("self-approval");
    }

    @Test
    @DisplayName("duplicate approver across stages → APPROVAL_ROUTE_INVALID (duplicate_stage_approver)")
    void duplicateApproverRejected() {
        assertThatThrownBy(() -> ApprovalRoute.multiStage("emp-sub", List.of("emp-a", "emp-a")))
                .isInstanceOf(ApprovalRouteInvalidException.class)
                .hasMessageContaining("duplicate_stage_approver");
    }

    @Test
    @DisplayName("self-approval (submitter == approver) → APPROVAL_ROUTE_INVALID")
    void selfApprovalRejected() {
        assertThatThrownBy(() -> ApprovalRoute.singleStage("emp-1", "emp-1"))
                .isInstanceOf(ApprovalRouteInvalidException.class)
                .hasMessageContaining("self-approval");
    }

    @Test
    @DisplayName("missing approver → APPROVAL_ROUTE_INVALID")
    void missingApproverRejected() {
        assertThatThrownBy(() -> ApprovalRoute.singleStage("emp-1", null))
                .isInstanceOf(ApprovalRouteInvalidException.class);
        assertThatThrownBy(() -> ApprovalRoute.singleStage("emp-1", "  "))
                .isInstanceOf(ApprovalRouteInvalidException.class);
    }

    @Test
    @DisplayName("SelfApprovalGuard: distinct ids pass")
    void selfApprovalGuardPass() {
        SelfApprovalGuard.ensureNotSelfApproval("emp-a", "emp-b");
    }
}

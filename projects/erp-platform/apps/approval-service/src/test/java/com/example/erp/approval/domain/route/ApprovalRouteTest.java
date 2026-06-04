package com.example.erp.approval.domain.route;

import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalRouteInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalRouteTest {

    @Test
    @DisplayName("valid single-stage route: submitter ≠ approver")
    void validRoute() {
        ApprovalRoute route = ApprovalRoute.singleStage("emp-submitter", "emp-approver");
        assertThat(route.approverId()).isEqualTo("emp-approver");
        assertThat(route.isApprover("emp-approver")).isTrue();
        assertThat(route.isApprover("emp-other")).isFalse();
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

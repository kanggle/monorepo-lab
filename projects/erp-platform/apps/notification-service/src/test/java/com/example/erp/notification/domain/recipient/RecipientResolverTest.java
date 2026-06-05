package com.example.erp.notification.domain.recipient;

import com.example.erp.notification.domain.notification.NotificationType;
import com.example.erp.notification.domain.render.ApprovalEvent;
import com.example.erp.notification.domain.render.DelegationEvent;
import com.example.erp.notification.domain.render.DelegationRevokedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recipient-mapping invariant table (architecture.md § Recipient
 * resolution): submitted→approver, approved→submitter, rejected→submitter,
 * withdrawn→approver.
 */
class RecipientResolverTest {

    private final RecipientResolver resolver = new RecipientResolver();

    private ApprovalEvent event(NotificationType type) {
        return new ApprovalEvent("evt-1", type, "erp", "appr-1", "DEPARTMENT", "dept-1",
                "emp-approver", "emp-submitter", null, null);
    }

    @Test
    void submittedNotifiesApprover() {
        assertThat(resolver.resolve(event(NotificationType.APPROVAL_SUBMITTED)).employeeId())
                .isEqualTo("emp-approver");
    }

    @Test
    void approvedNotifiesSubmitter() {
        assertThat(resolver.resolve(event(NotificationType.APPROVAL_APPROVED)).employeeId())
                .isEqualTo("emp-submitter");
    }

    @Test
    void rejectedNotifiesSubmitter() {
        assertThat(resolver.resolve(event(NotificationType.APPROVAL_REJECTED)).employeeId())
                .isEqualTo("emp-submitter");
    }

    @Test
    void withdrawnNotifiesApprover() {
        // The submitter withdrew their OWN request; the approver who had it
        // pending is the one to be told.
        assertThat(resolver.resolve(event(NotificationType.APPROVAL_WITHDRAWN)).employeeId())
                .isEqualTo("emp-approver");
    }

    @Test
    void delegatedNotifiesDelegate() {
        // TASK-ERP-BE-014: the employee who RECEIVED the delegation authority.
        DelegationEvent d = new DelegationEvent("evt-d", "erp", "dgr-1",
                "emp-A", "emp-D", "2026-06-06T00:00:00Z", null, null);
        assertThat(resolver.resolve(d).employeeId()).isEqualTo("emp-D");
    }

    @Test
    void revokedNotifiesDelegate() {
        // TASK-ERP-BE-016: the employee who LOSES the delegated authority.
        DelegationRevokedEvent r = new DelegationRevokedEvent("evt-r", "erp", "dgr-1",
                "emp-A", "emp-D", "휴가 복귀");
        assertThat(resolver.resolve(r).employeeId()).isEqualTo("emp-D");
    }
}

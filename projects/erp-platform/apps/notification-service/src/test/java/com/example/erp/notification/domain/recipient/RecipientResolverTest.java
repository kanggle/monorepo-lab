package com.example.erp.notification.domain.recipient;

import com.example.erp.notification.domain.notification.NotificationType;
import com.example.erp.notification.domain.render.ApprovalEvent;
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
}

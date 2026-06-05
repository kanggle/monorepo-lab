package com.example.erp.notification.domain.render;

import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.NotificationType;
import com.example.erp.notification.domain.notification.SourceRef;
import com.example.erp.notification.domain.recipient.Recipient;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Title/body rendering, incl. reason on reject/withdraw and reason-absent on approved. */
class NotificationFactoryTest {

    private final NotificationFactory factory = new NotificationFactory();
    private final Instant now = Instant.parse("2026-06-05T10:00:00Z");

    private ApprovalEvent event(NotificationType type, String finalizedAt, String reason) {
        return new ApprovalEvent("evt-1", type, "erp", "appr-1", "DEPARTMENT", "dept-1",
                "emp-approver", "emp-submitter", finalizedAt, reason);
    }

    @Test
    void submittedRendersTitleAndIdsBody() {
        Notification n = factory.from(event(NotificationType.APPROVAL_SUBMITTED, null, null),
                new Recipient("emp-approver"), "ntf-1", now);
        assertThat(n.title()).isEqualTo("결재 요청 도착");
        assertThat(n.recipientId()).isEqualTo("emp-approver");
        assertThat(n.type()).isEqualTo(NotificationType.APPROVAL_SUBMITTED);
        assertThat(n.source().sourceType()).isEqualTo(SourceRef.SourceType.APPROVAL);
        assertThat(n.source().sourceId()).isEqualTo("appr-1");
        assertThat(n.body()).contains("approvalRequestId=appr-1", "submitterId=emp-submitter");
        assertThat(n.read()).isFalse();
        assertThat(n.readAt()).isEmpty();
        assertThat(n.tenantId()).isEqualTo("erp");
    }

    @Test
    void approvedWithoutReasonOmitsReason() {
        Notification n = factory.from(
                event(NotificationType.APPROVAL_APPROVED, "2026-06-05T11:00:00Z", null),
                new Recipient("emp-submitter"), "ntf-2", now);
        assertThat(n.title()).isEqualTo("결재 승인됨");
        assertThat(n.body()).contains("finalizedAt=2026-06-05T11:00:00Z");
        assertThat(n.body()).doesNotContain("reason=");
    }

    @Test
    void rejectedIncludesReason() {
        Notification n = factory.from(
                event(NotificationType.APPROVAL_REJECTED, "2026-06-05T11:00:00Z", "예산 근거 부족"),
                new Recipient("emp-submitter"), "ntf-3", now);
        assertThat(n.title()).isEqualTo("결재 반려됨");
        assertThat(n.body()).contains("reason=예산 근거 부족");
    }

    @Test
    void withdrawnIncludesReason() {
        Notification n = factory.from(
                event(NotificationType.APPROVAL_WITHDRAWN, "2026-06-05T11:00:00Z", "기안 내용 수정 필요"),
                new Recipient("emp-approver"), "ntf-4", now);
        assertThat(n.title()).isEqualTo("결재 회수됨");
        assertThat(n.body()).contains("reason=기안 내용 수정 필요");
    }
}

package com.example.erp.notification.domain.render;

import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.SourceRef;
import com.example.erp.notification.domain.recipient.Recipient;

import java.time.Instant;

/**
 * Renders an {@link ApprovalEvent} into a {@link Notification} (title + body
 * composed from the payload ids + reason). Pure module — no framework, no
 * outbound call. Ids-only display (no display-name resolution in v1, § Scope
 * discipline); a name is never fabricated when absent (E5 spirit).
 *
 * <p>Title / body mapping (architecture.md § Recipient resolution):
 * <ul>
 *   <li>SUBMITTED → "결재 요청 도착" (approvalRequestId, subject, submitter)</li>
 *   <li>APPROVED  → "결재 승인됨" (approvalRequestId, approver, finalizedAt; reason if present)</li>
 *   <li>REJECTED  → "결재 반려됨" (approvalRequestId, approver, finalizedAt, reason)</li>
 *   <li>WITHDRAWN → "결재 회수됨" (approvalRequestId, submitter, finalizedAt, reason)</li>
 * </ul>
 */
public final class NotificationFactory {

    /** Builds the in-app notification for the resolved recipient. */
    public Notification from(ApprovalEvent event, Recipient recipient, String id, Instant createdAt) {
        String title = title(event);
        String body = body(event);
        return Notification.create(
                id,
                event.tenantId(),
                recipient.employeeId(),
                event.type(),
                title,
                body,
                SourceRef.approval(event.approvalRequestId()),
                createdAt);
    }

    private String title(ApprovalEvent event) {
        return switch (event.type()) {
            case APPROVAL_SUBMITTED -> "결재 요청 도착";
            case APPROVAL_APPROVED -> "결재 승인됨";
            case APPROVAL_REJECTED -> "결재 반려됨";
            case APPROVAL_WITHDRAWN -> "결재 회수됨";
        };
    }

    private String body(ApprovalEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("approvalRequestId=").append(event.approvalRequestId());
        switch (event.type()) {
            case APPROVAL_SUBMITTED -> {
                appendSubject(sb, event);
                sb.append(", submitterId=").append(event.submitterId());
            }
            case APPROVAL_APPROVED -> {
                sb.append(", approverId=").append(event.approverId());
                appendFinalizedAt(sb, event);
                event.reasonOpt().ifPresent(r -> sb.append(", reason=").append(r));
            }
            case APPROVAL_REJECTED -> {
                sb.append(", approverId=").append(event.approverId());
                appendFinalizedAt(sb, event);
                event.reasonOpt().ifPresent(r -> sb.append(", reason=").append(r));
            }
            case APPROVAL_WITHDRAWN -> {
                sb.append(", submitterId=").append(event.submitterId());
                appendFinalizedAt(sb, event);
                event.reasonOpt().ifPresent(r -> sb.append(", reason=").append(r));
            }
        }
        return sb.toString();
    }

    private void appendSubject(StringBuilder sb, ApprovalEvent event) {
        if (event.subjectType() != null) {
            sb.append(", subjectType=").append(event.subjectType());
        }
        if (event.subjectId() != null) {
            sb.append(", subjectId=").append(event.subjectId());
        }
    }

    private void appendFinalizedAt(StringBuilder sb, ApprovalEvent event) {
        event.finalizedAtOpt().ifPresent(f -> sb.append(", finalizedAt=").append(f));
    }
}

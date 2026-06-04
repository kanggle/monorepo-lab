package com.example.erp.approval.application.view;

import com.example.erp.approval.domain.request.ApprovalAction;
import com.example.erp.approval.domain.request.ApprovalRequest;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Detail payload (approval-api.md § Common shapes {@code ApprovalRequest}).
 * Nullable fields ({@code reason}, {@code submittedAt}, {@code finalizedAt}) are
 * ABSENT per {@code @JsonInclude(NON_NULL)} — never serialized as {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalRequestView(String id, String status, String subjectType,
                                  String subjectId, String title, String approverId,
                                  String submitterId, String reason,
                                  List<ApprovalHistoryEntry> history,
                                  Instant createdAt, Instant submittedAt,
                                  Instant finalizedAt) {

    public static ApprovalRequestView from(ApprovalRequest r, List<ApprovalAction> actions) {
        List<ApprovalHistoryEntry> history = actions.stream()
                .map(ApprovalHistoryEntry::from)
                .toList();
        return new ApprovalRequestView(
                r.getId(),
                r.getStatus().name(),
                r.getSubjectType().name(),
                r.getSubjectId(),
                r.getTitle(),
                r.getApproverId(),
                r.getSubmitterId(),
                r.getCreationReason(),
                history,
                r.getCreatedAt(),
                r.getSubmittedAt(),
                r.getFinalizedAt());
    }
}

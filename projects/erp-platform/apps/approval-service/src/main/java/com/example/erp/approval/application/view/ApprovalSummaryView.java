package com.example.erp.approval.application.view;

import com.example.erp.approval.domain.request.ApprovalRequest;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * List / inbox item (approval-api.md § Common shapes {@code ApprovalSummary}) —
 * trimmed, no {@code history}. {@code submittedAt} is ABSENT until SUBMITTED
 * ({@code @JsonInclude(NON_NULL)}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalSummaryView(String id, String status, String subjectType,
                                  String subjectId, String title, String approverId,
                                  String submitterId, Instant createdAt,
                                  Instant submittedAt) {

    public static ApprovalSummaryView from(ApprovalRequest r) {
        return new ApprovalSummaryView(
                r.getId(),
                r.getStatus().name(),
                r.getSubjectType().name(),
                r.getSubjectId(),
                r.getTitle(),
                r.getApproverId(),
                r.getSubmitterId(),
                r.getCreatedAt(),
                r.getSubmittedAt());
    }
}

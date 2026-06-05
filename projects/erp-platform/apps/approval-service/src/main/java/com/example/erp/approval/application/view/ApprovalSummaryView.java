package com.example.erp.approval.application.view;

import com.example.erp.approval.domain.request.ApprovalRequest;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * List / inbox item (approval-api.md § Common shapes {@code ApprovalSummary} +
 * v2.0 amendment) — trimmed, no {@code history}/{@code stages}. {@code submittedAt}
 * and {@code currentStage} are ABSENT when unset ({@code @JsonInclude(NON_NULL)}).
 * {@code approverId} = the current stage's approver; {@code currentStage} = the
 * 0-based pending stage (ABSENT once finalized); {@code totalStages} = route length.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalSummaryView(String id, String status, String subjectType,
                                  String subjectId, String title, String approverId,
                                  String submitterId, Integer currentStage,
                                  int totalStages, Instant createdAt,
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
                r.isFinalized() ? null : r.getCurrentStageIndex(),
                r.getTotalStages(),
                r.getCreatedAt(),
                r.getSubmittedAt());
    }
}

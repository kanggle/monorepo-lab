package com.example.erp.approval.application.view;

import com.example.erp.approval.domain.request.ApprovalAction;
import com.example.erp.approval.domain.request.ApprovalRequest;
import com.example.erp.approval.domain.request.ApprovalStatus;
import com.example.erp.approval.domain.route.ApprovalRouteStage;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Detail payload (approval-api.md § Common shapes {@code ApprovalRequest} + v2.0
 * amendment). Nullable fields ({@code reason}, {@code submittedAt},
 * {@code finalizedAt}, {@code currentStage}) are ABSENT per
 * {@code @JsonInclude(NON_NULL)} — never serialized as {@code null}.
 *
 * <p>v2.0 (TASK-ERP-BE-012): {@code stages} = ordered route stages with per-stage
 * status (PENDING|APPROVED); {@code currentStage} = 0-based index of the pending
 * stage (ABSENT once finalized); {@code totalStages} = route length.
 * {@code approverId} = the current stage's approver (read back-compat).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalRequestView(String id, String status, String subjectType,
                                  String subjectId, String title, String approverId,
                                  String submitterId, String reason,
                                  List<StageView> stages, Integer currentStage,
                                  int totalStages,
                                  List<ApprovalHistoryEntry> history,
                                  Instant createdAt, Instant submittedAt,
                                  Instant finalizedAt) {

    /** One route stage in the detail/summary response (PENDING until its turn approves). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StageView(int stageIndex, String approverId, String status) {
    }

    public static ApprovalRequestView from(ApprovalRequest r, List<ApprovalAction> actions,
                                           List<ApprovalRouteStage> routeStages) {
        List<ApprovalHistoryEntry> history = actions.stream()
                .map(ApprovalHistoryEntry::from)
                .toList();
        List<StageView> stages = stageViews(r, routeStages);
        // currentStage ABSENT once finalized (approval-api.md § v2.0 amendment).
        Integer currentStage = r.isFinalized() ? null : r.getCurrentStageIndex();
        return new ApprovalRequestView(
                r.getId(),
                r.getStatus().name(),
                r.getSubjectType().name(),
                r.getSubjectId(),
                r.getTitle(),
                r.getApproverId(),
                r.getSubmitterId(),
                r.getCreationReason(),
                stages,
                currentStage,
                r.getTotalStages(),
                history,
                r.getCreatedAt(),
                r.getSubmittedAt(),
                r.getFinalizedAt());
    }

    /**
     * Build the per-stage status list. A stage is APPROVED when it is before the
     * current position, or when the whole request is APPROVED (all stages
     * approved); otherwise PENDING. For a REJECTED/WITHDRAWN request the stages
     * before the acting stage stay APPROVED, the rest PENDING (the request
     * finalized without completing them).
     */
    private static List<StageView> stageViews(ApprovalRequest r, List<ApprovalRouteStage> routeStages) {
        List<StageView> out = new ArrayList<>(routeStages.size());
        boolean fullyApproved = r.getStatus() == ApprovalStatus.APPROVED;
        for (ApprovalRouteStage s : routeStages) {
            boolean approved = fullyApproved || s.getStageIndex() < r.getCurrentStageIndex();
            out.add(new StageView(s.getStageIndex(), s.getApproverId(),
                    approved ? "APPROVED" : "PENDING"));
        }
        return out;
    }
}

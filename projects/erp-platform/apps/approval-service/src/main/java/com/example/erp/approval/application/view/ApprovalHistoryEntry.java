package com.example.erp.approval.application.view;

import com.example.erp.approval.domain.request.ApprovalAction;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * One immutable audit entry per transition (approval-api.md § Common shapes
 * {@code ApprovalHistoryEntry}). {@code reason}, the v2.0 {@code stage} (0-based;
 * TASK-ERP-BE-012), and the v2.1 {@code actingForApproverId} (the stage approver
 * A a delegate acted for; TASK-ERP-BE-013) are ABSENT when none
 * ({@code @JsonInclude(NON_NULL)}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalHistoryEntry(String transition, String actor, Instant at,
                                   String reason, Integer stage,
                                   String actingForApproverId) {

    public static ApprovalHistoryEntry from(ApprovalAction a) {
        return new ApprovalHistoryEntry(a.getTransition().name(), a.getActor(),
                a.getOccurredAt(), a.getReason(), a.getStage(), a.getOnBehalfOf());
    }
}

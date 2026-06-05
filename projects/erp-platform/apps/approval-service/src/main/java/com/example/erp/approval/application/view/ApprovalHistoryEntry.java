package com.example.erp.approval.application.view;

import com.example.erp.approval.domain.request.ApprovalAction;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * One immutable audit entry per transition (approval-api.md § Common shapes
 * {@code ApprovalHistoryEntry}). {@code reason} and the v2.0 {@code stage}
 * (0-based; TASK-ERP-BE-012) are ABSENT when none ({@code @JsonInclude(NON_NULL)}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalHistoryEntry(String transition, String actor, Instant at,
                                   String reason, Integer stage) {

    public static ApprovalHistoryEntry from(ApprovalAction a) {
        return new ApprovalHistoryEntry(a.getTransition().name(), a.getActor(),
                a.getOccurredAt(), a.getReason(), a.getStage());
    }
}

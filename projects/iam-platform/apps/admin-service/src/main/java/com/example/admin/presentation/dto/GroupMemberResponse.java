package com.example.admin.presentation.dto;

import com.example.admin.application.GroupAdminUseCase;

import java.time.Instant;

/** TASK-BE-520 (ADR-MONO-046) — a group member wire shape. */
public record GroupMemberResponse(
        String operatorId,
        String displayName,
        Instant addedAt
) {
    public static GroupMemberResponse from(GroupAdminUseCase.MemberView v) {
        return new GroupMemberResponse(v.operatorId(), v.displayName(), v.addedAt());
    }
}

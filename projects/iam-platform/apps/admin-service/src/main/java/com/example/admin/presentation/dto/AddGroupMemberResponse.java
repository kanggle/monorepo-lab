package com.example.admin.presentation.dto;

import com.example.admin.application.GroupAdminUseCase;

import java.time.Instant;

/**
 * TASK-BE-520 (ADR-MONO-046) — {@code POST /api/admin/groups/{groupId}/members} 201 body.
 * {@code fannedOutGrants} = grants newly materialised onto this member (equal existing direct
 * grants are idempotent-skipped and not counted).
 */
public record AddGroupMemberResponse(
        String operatorId,
        String displayName,
        Instant addedAt,
        int fannedOutGrants
) {
    public static AddGroupMemberResponse from(GroupAdminUseCase.MemberAddResult v) {
        return new AddGroupMemberResponse(v.operatorId(), v.displayName(), v.addedAt(), v.fannedOutGrants());
    }
}

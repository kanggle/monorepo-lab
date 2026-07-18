package com.example.admin.presentation.dto;

import com.example.admin.application.GroupAdminUseCase;

import java.util.List;

/**
 * TASK-BE-520 (ADR-MONO-046) — {@code POST /api/admin/groups/{groupId}/grants} 201 body: the
 * created grant templates + the total rows materialised across all current members
 * ({@code fannedOutRows}; equal existing direct grants are idempotent-skipped).
 */
public record AddGroupGrantsResponse(
        List<GroupGrantResponse> items,
        int fannedOutRows
) {
    public static AddGroupGrantsResponse from(GroupAdminUseCase.GrantAddResult v) {
        return new AddGroupGrantsResponse(
                v.items().stream().map(GroupGrantResponse::from).toList(), v.fannedOutRows());
    }
}

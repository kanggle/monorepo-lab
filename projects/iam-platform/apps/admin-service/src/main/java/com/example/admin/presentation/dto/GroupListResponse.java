package com.example.admin.presentation.dto;

import com.example.admin.application.GroupAdminUseCase;

import java.util.List;

/** TASK-BE-520 (ADR-MONO-046) — {@code GET /api/admin/groups} paginated wire shape. */
public record GroupListResponse(
        List<GroupResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static GroupListResponse from(GroupAdminUseCase.GroupPage p) {
        return new GroupListResponse(
                p.items().stream().map(GroupResponse::from).toList(),
                p.page(), p.size(), p.totalElements(), p.totalPages());
    }
}

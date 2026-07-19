package com.example.admin.presentation.dto;

import com.example.admin.application.GroupAdminUseCase;

import java.time.Instant;

/** TASK-BE-520 (ADR-MONO-046) — the group wire shape (admin-api.md § Group wire shape). */
public record GroupResponse(
        String groupId,
        String tenantId,
        String name,
        String description,
        long memberCount,
        long grantCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static GroupResponse from(GroupAdminUseCase.GroupView v) {
        return new GroupResponse(v.groupId(), v.tenantId(), v.name(), v.description(),
                v.memberCount(), v.grantCount(), v.createdAt(), v.updatedAt());
    }
}

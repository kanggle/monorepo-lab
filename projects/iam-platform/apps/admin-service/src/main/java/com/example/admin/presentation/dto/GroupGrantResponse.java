package com.example.admin.presentation.dto;

import com.example.admin.application.GroupAdminUseCase;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * TASK-BE-520 (ADR-MONO-046) — a group grant wire shape. {@code roleName} is present only for
 * {@code type=ROLE}; {@code tenantId} only for {@code type=TENANT_ASSIGNMENT} — the null field
 * is omitted (admin-api.md § Group grant wire shape).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupGrantResponse(
        String grantId,
        String type,
        String roleName,
        String tenantId,
        Instant grantedAt
) {
    public static GroupGrantResponse from(GroupAdminUseCase.GrantView v) {
        return new GroupGrantResponse(v.grantId(), v.type(), v.roleName(), v.tenantId(), v.grantedAt());
    }
}

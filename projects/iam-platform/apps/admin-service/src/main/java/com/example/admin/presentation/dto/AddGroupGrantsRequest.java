package com.example.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * TASK-BE-520 (ADR-MONO-046) — {@code POST /api/admin/groups/{groupId}/grants} body. At least
 * one of {@code roles} / {@code tenantAssignments} must be non-empty (enforced in the
 * use-case → VALIDATION_ERROR).
 */
public record AddGroupGrantsRequest(
        List<String> roles,
        List<TenantAssignment> tenantAssignments
) {
    public record TenantAssignment(@NotBlank String tenantId) {}

    /** Flattened tenant ids (null-safe). */
    public List<String> tenantIds() {
        return tenantAssignments == null ? List.of()
                : tenantAssignments.stream().map(TenantAssignment::tenantId).toList();
    }
}

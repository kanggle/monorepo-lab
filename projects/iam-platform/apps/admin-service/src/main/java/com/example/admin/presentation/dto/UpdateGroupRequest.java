package com.example.admin.presentation.dto;

import jakarta.validation.constraints.Size;

/**
 * TASK-BE-520 (ADR-MONO-046) — {@code PATCH /api/admin/groups/{groupId}} body. Both fields
 * optional; the controller rejects an all-null body with VALIDATION_ERROR.
 */
public record UpdateGroupRequest(
        @Size(min = 1, max = 120) String name,
        @Size(max = 255) String description
) {}

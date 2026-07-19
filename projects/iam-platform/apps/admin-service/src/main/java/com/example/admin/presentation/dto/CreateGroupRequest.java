package com.example.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TASK-BE-520 (ADR-MONO-046) — {@code POST /api/admin/groups} body. {@code tenantId != '*'}
 * and actor-scope are enforced in the use-case (VALIDATION_ERROR / TENANT_SCOPE_DENIED).
 */
public record CreateGroupRequest(
        @NotBlank String tenantId,
        @NotBlank @Size(min = 1, max = 120) String name,
        @Size(max = 255) String description
) {}

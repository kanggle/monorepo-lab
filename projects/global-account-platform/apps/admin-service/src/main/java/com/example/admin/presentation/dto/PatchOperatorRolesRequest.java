package com.example.admin.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for {@code PATCH /api/admin/operators/{operatorId}/roles}.
 * {@code roles} may be empty — spec allows removing every role.
 */
public record PatchOperatorRolesRequest(
        @NotNull
        List<String> roles
) {}

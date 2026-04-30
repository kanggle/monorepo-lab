package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * TASK-BE-231: Request DTO for PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles.
 * An empty roles list removes all role assignments.
 */
public record AssignRolesRequest(

        @NotNull(message = "roles must not be null (use empty array to remove all roles)")
        List<@Size(max = 50, message = "each role must be at most 50 characters") String> roles,

        String operatorId
) {
}

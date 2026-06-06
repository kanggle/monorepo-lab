package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * TASK-BE-231: Request DTO for PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles.
 * An empty roles list removes all role assignments.
 *
 * <p>TASK-BE-265: role_name max length aligned with DB (VARCHAR(64)) and
 * {@code AccountRoleName} validator. Previously {@code @Size(max = 50)} caused
 * a regression: a 51–64 char role inserted via {@code /roles:add} could not be
 * re-sent through this replace-all endpoint. {@code operatorId} is bounded by
 * the {@code granted_by VARCHAR(36)} column.
 */
public record AssignRolesRequest(

        @NotNull(message = "roles must not be null (use empty array to remove all roles)")
        List<@Size(max = 64, message = "each role must be at most 64 characters") String> roles,

        @Size(max = 36, message = "operatorId must be at most 36 characters")
        String operatorId
) {
}

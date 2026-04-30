package com.example.account.presentation.dto.response;

import com.example.account.application.result.AssignRolesResult;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-231: Response DTO for PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles.
 */
public record AssignRolesResponse(
        String accountId,
        String tenantId,
        List<String> roles,
        Instant updatedAt
) {
    public static AssignRolesResponse from(AssignRolesResult result) {
        return new AssignRolesResponse(
                result.accountId(),
                result.tenantId(),
                result.roles(),
                result.updatedAt()
        );
    }
}

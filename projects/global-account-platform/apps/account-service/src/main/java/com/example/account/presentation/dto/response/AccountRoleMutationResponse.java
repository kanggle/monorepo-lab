package com.example.account.presentation.dto.response;

import com.example.account.application.result.AccountRoleMutationResult;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-255: Response DTO for the single-role add/remove endpoints. Mirrors
 * the shape of {@code AssignRolesResponse} so clients can consume both
 * endpoints with a single response model.
 */
public record AccountRoleMutationResponse(
        String accountId,
        String tenantId,
        List<String> roles,
        Instant updatedAt
) {
    public static AccountRoleMutationResponse from(AccountRoleMutationResult result) {
        return new AccountRoleMutationResponse(
                result.accountId(),
                result.tenantId(),
                result.roles(),
                result.updatedAt()
        );
    }
}

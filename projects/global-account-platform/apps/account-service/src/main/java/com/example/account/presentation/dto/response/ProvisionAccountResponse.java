package com.example.account.presentation.dto.response;

import com.example.account.application.result.ProvisionAccountResult;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-231: Response DTO for POST /internal/tenants/{tenantId}/accounts.
 * Sensitive fields (password_hash, deleted_at, email_hash) are excluded.
 */
public record ProvisionAccountResponse(
        String accountId,
        String tenantId,
        String email,
        String status,
        List<String> roles,
        Instant createdAt
) {
    public static ProvisionAccountResponse from(ProvisionAccountResult result) {
        return new ProvisionAccountResponse(
                result.accountId(),
                result.tenantId(),
                result.email(),
                result.status(),
                result.roles(),
                result.createdAt()
        );
    }
}

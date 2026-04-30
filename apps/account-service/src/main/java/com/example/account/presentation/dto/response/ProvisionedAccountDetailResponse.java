package com.example.account.presentation.dto.response;

import com.example.account.application.result.ProvisionedAccountDetailResult;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-231: Response DTO for GET /internal/tenants/{tenantId}/accounts/{accountId}.
 * Sensitive fields (password_hash, deleted_at, email_hash) are excluded per regulated trait R4.
 */
public record ProvisionedAccountDetailResponse(
        String accountId,
        String tenantId,
        String email,
        String status,
        List<String> roles,
        String displayName,
        Instant createdAt,
        Instant emailVerifiedAt
) {
    public static ProvisionedAccountDetailResponse from(ProvisionedAccountDetailResult result) {
        return new ProvisionedAccountDetailResponse(
                result.accountId(),
                result.tenantId(),
                result.email(),
                result.status(),
                result.roles(),
                result.displayName(),
                result.createdAt(),
                result.emailVerifiedAt()
        );
    }
}

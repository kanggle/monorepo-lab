package com.example.account.presentation.dto.response;

import com.example.account.application.result.ProvisionPasswordResetResult;

import java.time.Instant;

/**
 * TASK-BE-231: Response DTO for POST /internal/tenants/{tenantId}/accounts/{accountId}/password-reset.
 */
public record ProvisionPasswordResetResponse(
        String accountId,
        String tenantId,
        Instant resetTokenIssuedAt,
        String message
) {
    public static ProvisionPasswordResetResponse from(ProvisionPasswordResetResult result) {
        return new ProvisionPasswordResetResponse(
                result.accountId(),
                result.tenantId(),
                result.resetTokenIssuedAt(),
                result.message()
        );
    }
}

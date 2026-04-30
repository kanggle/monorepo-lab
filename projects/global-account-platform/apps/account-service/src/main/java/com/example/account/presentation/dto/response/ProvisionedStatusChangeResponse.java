package com.example.account.presentation.dto.response;

import com.example.account.application.result.ProvisionedStatusChangeResult;

import java.time.Instant;

/**
 * TASK-BE-231: Response DTO for PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status.
 */
public record ProvisionedStatusChangeResponse(
        String accountId,
        String tenantId,
        String previousStatus,
        String currentStatus,
        Instant changedAt
) {
    public static ProvisionedStatusChangeResponse from(ProvisionedStatusChangeResult result) {
        return new ProvisionedStatusChangeResponse(
                result.accountId(),
                result.tenantId(),
                result.previousStatus(),
                result.currentStatus(),
                result.changedAt()
        );
    }
}

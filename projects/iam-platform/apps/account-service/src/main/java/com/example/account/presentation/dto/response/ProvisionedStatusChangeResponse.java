package com.example.account.presentation.dto.response;

import com.example.account.application.result.ProvisionedStatusChangeResult;

import java.time.Instant;

/**
 * TASK-BE-231: Response DTO for PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status.
 *
 * <p>TASK-BE-622 — {@code scope}: {@code ACCOUNT} | {@code SITE_MEMBERSHIP} (account-internal-provisioning.md
 * § Consumer-pool member). With {@code SITE_MEMBERSHIP} the two statuses are the path site's membership's.
 */
public record ProvisionedStatusChangeResponse(
        String accountId,
        String tenantId,
        String previousStatus,
        String currentStatus,
        Instant changedAt,
        String scope
) {
    public static ProvisionedStatusChangeResponse from(ProvisionedStatusChangeResult result) {
        return new ProvisionedStatusChangeResponse(
                result.accountId(),
                result.tenantId(),
                result.previousStatus(),
                result.currentStatus(),
                result.changedAt(),
                result.scope()
        );
    }
}

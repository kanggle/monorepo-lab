package com.example.admin.application;

import java.time.Instant;

/**
 * TASK-BE-621 — {@code scope} ({@link GdprAdminUseCase#SCOPE_ACCOUNT} | {@link GdprAdminUseCase#SCOPE_SITE_MEMBERSHIP})
 * and {@code siteTenantId}: a site operator's lock of a consumer-pool member locks only that site's membership
 * (statuses are then the membership's). See admin-api.md § lock.
 */
public record LockAccountResult(
        String accountId,
        String previousStatus,
        String currentStatus,
        String operatorId,
        Instant lockedAt,
        String auditId,
        String scope,
        String siteTenantId
) {
    public LockAccountResult(String accountId, String previousStatus, String currentStatus, String operatorId,
                             Instant lockedAt, String auditId) {
        this(accountId, previousStatus, currentStatus, operatorId, lockedAt, auditId,
                GdprAdminUseCase.SCOPE_ACCOUNT, null);
    }
}

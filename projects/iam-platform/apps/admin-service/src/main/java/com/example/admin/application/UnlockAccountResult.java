package com.example.admin.application;

import java.time.Instant;

/** TASK-BE-621 — {@code scope} / {@code siteTenantId}: see {@link LockAccountResult}. */
public record UnlockAccountResult(
        String accountId,
        String previousStatus,
        String currentStatus,
        String operatorId,
        Instant unlockedAt,
        String auditId,
        String scope,
        String siteTenantId
) {
    public UnlockAccountResult(String accountId, String previousStatus, String currentStatus, String operatorId,
                               Instant unlockedAt, String auditId) {
        this(accountId, previousStatus, currentStatus, operatorId, unlockedAt, auditId,
                GdprAdminUseCase.SCOPE_ACCOUNT, null);
    }
}

package com.example.account.application.result;

import java.time.Instant;

/**
 * TASK-BE-602 — an account's status together with the tenant its row actually lives in.
 *
 * <p>Answer to {@code GET /internal/accounts/{id}/status-with-tenant}: the caller does not
 * supply a tenant, it receives one ({@code tenantId} = the {@code accounts.tenant_id} of the
 * row found by its primary key).
 */
public record AccountStatusWithTenantResult(
        String accountId,
        String tenantId,
        String status,
        Instant statusChangedAt
) {
}

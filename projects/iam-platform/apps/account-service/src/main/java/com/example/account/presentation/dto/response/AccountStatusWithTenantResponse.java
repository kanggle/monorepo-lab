package com.example.account.presentation.dto.response;

import com.example.account.application.result.AccountStatusWithTenantResult;

import java.time.Instant;

/**
 * TASK-BE-602 — response of {@code GET /internal/accounts/{id}/status-with-tenant}
 * (auth-to-account.md). No PII: id, tenant, status and its timestamp only.
 */
public record AccountStatusWithTenantResponse(
        String accountId,
        String tenantId,
        String status,
        Instant statusChangedAt
) {
    public static AccountStatusWithTenantResponse from(AccountStatusWithTenantResult result) {
        return new AccountStatusWithTenantResponse(
                result.accountId(),
                result.tenantId(),
                result.status(),
                result.statusChangedAt()
        );
    }
}

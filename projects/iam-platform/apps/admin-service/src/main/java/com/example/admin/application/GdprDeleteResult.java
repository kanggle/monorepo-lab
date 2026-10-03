package com.example.admin.application;

import java.time.Instant;

/**
 * TASK-BE-619 — {@code scope}: {@code ACCOUNT} (the account was erased) or {@code SITE_MEMBERSHIP} (a site
 * operator's request on a consumer-pool member ended only {@code siteTenantId}'s membership — nothing was
 * erased, {@code maskedAt} is null). See admin-api.md § gdpr-delete.
 */
public record GdprDeleteResult(
        String accountId,
        String status,
        Instant maskedAt,
        String auditId,
        String scope,
        String siteTenantId
) {
    /** Pre-619 shape — the account was erased. */
    public GdprDeleteResult(String accountId, String status, Instant maskedAt, String auditId) {
        this(accountId, status, maskedAt, auditId, GdprAdminUseCase.SCOPE_ACCOUNT, null);
    }
}

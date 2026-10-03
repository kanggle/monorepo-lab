package com.example.admin.presentation.dto;

import java.time.Instant;

/**
 * Response of {@code POST /api/admin/accounts/{accountId}/gdpr-delete} (admin-api.md).
 *
 * <p>TASK-BE-619 — {@code scope} ({@code ACCOUNT} | {@code SITE_MEMBERSHIP}) and {@code siteTenantId}: a site
 * operator's request on a consumer-pool member ends only that site's membership ({@code maskedAt = null}).
 */
public record GdprDeleteResponse(
        String accountId,
        String status,
        Instant maskedAt,
        String auditId,
        String scope,
        String siteTenantId
) {}

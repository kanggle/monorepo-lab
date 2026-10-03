package com.example.account.presentation.dto.response;

import com.example.account.application.result.GdprDeleteResult;

import java.time.Instant;

/**
 * Response of {@code POST /internal/accounts/{id}/gdpr-delete} (admin-to-account.md).
 *
 * <p>TASK-BE-619 — {@code scope} ({@code ACCOUNT} | {@code SITE_MEMBERSHIP}) and {@code siteTenantId}
 * say what was erased; see {@link GdprDeleteResult}.
 */
public record GdprDeleteResponse(
        String accountId,
        String status,
        String emailHash,
        Instant maskedAt,
        String scope,
        String siteTenantId
) {
    public static GdprDeleteResponse from(GdprDeleteResult result) {
        return new GdprDeleteResponse(
                result.accountId(),
                result.status(),
                result.emailHash(),
                result.maskedAt(),
                result.scope(),
                result.siteTenantId()
        );
    }
}

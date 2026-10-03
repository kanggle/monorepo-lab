package com.example.account.presentation.dto.response;

import com.example.account.application.result.DeleteAccountResult;

import java.time.Instant;

/** TASK-BE-619 — {@code scope} / {@code siteTenantId}: see {@link DeleteAccountResult}. */
public record DeleteAccountResponse(
        String accountId,
        String previousStatus,
        String currentStatus,
        Instant gracePeriodEndsAt,
        String scope,
        String siteTenantId
) {
    public static DeleteAccountResponse from(DeleteAccountResult result) {
        return new DeleteAccountResponse(
                result.accountId(),
                result.previousStatus(),
                result.currentStatus(),
                result.gracePeriodEndsAt(),
                result.scope(),
                result.siteTenantId()
        );
    }
}

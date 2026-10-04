package com.example.account.presentation.dto.response;

import com.example.account.application.result.StatusChangeResult;

import java.time.Instant;

/** TASK-BE-621 — {@code scope} / {@code siteTenantId}: see {@link StatusChangeResult}. */
public record StatusChangeResponse(
        String accountId,
        String previousStatus,
        String currentStatus,
        Instant changedAt,
        String scope,
        String siteTenantId
) {
    public static StatusChangeResponse from(StatusChangeResult result) {
        return new StatusChangeResponse(
                result.accountId(),
                result.previousStatus(),
                result.currentStatus(),
                result.changedAt(),
                result.scope(),
                result.siteTenantId()
        );
    }
}

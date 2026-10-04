package com.example.admin.presentation.dto;

import java.time.Instant;

/** TASK-BE-621 — {@code scope} ({@code ACCOUNT} | {@code SITE_MEMBERSHIP}) · {@code siteTenantId}: admin-api.md § unlock. */
public record UnlockAccountResponse(
        String accountId,
        String previousStatus,
        String currentStatus,
        String operatorId,
        Instant unlockedAt,
        String auditId,
        String scope,
        String siteTenantId
) {}

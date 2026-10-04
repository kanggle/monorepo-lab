package com.example.admin.presentation.dto;

import java.time.Instant;

/** TASK-BE-621 — {@code scope} ({@code ACCOUNT} | {@code SITE_MEMBERSHIP}) · {@code siteTenantId}: admin-api.md § lock. */
public record LockAccountResponse(
        String accountId,
        String previousStatus,
        String currentStatus,
        String operatorId,
        Instant lockedAt,
        String auditId,
        String scope,
        String siteTenantId
) {}

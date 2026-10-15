package com.example.admin.application.tenant;

import com.example.admin.application.OperatorContext;

/**
 * {@code POST /api/admin/tenants} (admin-api.md § Tenant Lifecycle) — TASK-MONO-773 S4: «tenant + first admin» is
 * one command; {@code firstAdminEmail} / {@code firstAdminDisplayName} are required (validated after trim by
 * {@link CreateTenantUseCase}, like the 772 issuance).
 */
public record CreateTenantCommand(
        String tenantId,
        String displayName,
        String tenantType,
        String firstAdminEmail,
        String firstAdminDisplayName,
        OperatorContext operator,
        String reason,
        String idempotencyKey
) {
}

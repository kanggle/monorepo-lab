package com.example.admin.application.tenant;

import com.example.admin.application.OperatorInvitationResult;

/**
 * TASK-MONO-773 S4 — what {@code POST /api/admin/tenants} made: the {@code PENDING} tenant and its first-admin
 * invitation (the invitation item, delivery included — never the token).
 */
public record CreateTenantResult(TenantSummary tenant, OperatorInvitationResult firstAdminInvitation) {
}

package com.example.admin.application;

/**
 * TASK-BE-631 — {@code DELETE /api/admin/operators/{operatorId}/domain-permission-sets/{setId}} (the personal
 * revoke: DIRECT + MIGRATION rows only). {@code requestedTenantId} is the raw {@code X-Tenant-Id}.
 */
public record RevokeDomainPermissionSetCommand(OperatorContext actor, String requestedTenantId, String operatorId,
                                               String setId, String reason) {}

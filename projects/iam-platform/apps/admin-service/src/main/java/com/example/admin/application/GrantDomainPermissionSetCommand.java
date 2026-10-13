package com.example.admin.application;

/**
 * TASK-BE-631 — {@code POST /api/admin/operators/{operatorId}/domain-permission-sets} (a DIRECT grant).
 * {@code requestedTenantId} is the raw {@code X-Tenant-Id} (null = the omitted-tenant rule).
 */
public record GrantDomainPermissionSetCommand(OperatorContext actor, String requestedTenantId, String operatorId,
                                              String setId, String reason) {}

package com.example.admin.application;

import com.example.admin.domain.rbac.DomainRoleCatalog.RoleRef;

import java.util.List;

/**
 * TASK-BE-631 — {@code POST /api/admin/domain-permission-sets}. {@code requestedTenantId} is the raw
 * {@code X-Tenant-Id} (null = the omitted-tenant rule); {@code roles} as sent (validated by the use case).
 */
public record CreateDomainPermissionSetCommand(OperatorContext actor, String requestedTenantId, String name,
                                               String description, List<RoleRef> roles, String reason,
                                               String idempotencyKey) {}

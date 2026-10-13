package com.example.admin.application;

import com.example.admin.domain.rbac.DomainRoleCatalog.RoleRef;

import java.util.List;

/**
 * TASK-BE-631 — {@code PATCH /api/admin/domain-permission-sets/{setId}}. A {@code null} field was not sent;
 * non-null {@code roles} replaces the whole role list. At least one field must be present.
 */
public record UpdateDomainPermissionSetCommand(OperatorContext actor, String requestedTenantId, String setId,
                                               String name, String description, List<RoleRef> roles,
                                               String reason) {}

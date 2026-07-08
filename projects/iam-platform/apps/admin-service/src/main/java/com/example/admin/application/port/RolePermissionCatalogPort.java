package com.example.admin.application.port;

import java.util.List;

/**
 * TASK-BE-486 — read-only port over the {@code admin_roles} +
 * {@code admin_role_permissions} tables for the console 「권한」 / 「권한 세트」
 * screens (TASK-PC-FE-227 / TASK-PC-FE-228).
 *
 * <p>Distinct from {@link AdminOperatorPort} (operator management) because the
 * role/permission catalog is a separate resource — roles and their permission
 * sets are seed/Flyway-owned reference data, never mutated through the admin
 * API (v1 is read-only; definition changes stay in migrations). This port keeps
 * the JPA entities out of the application layer, returning immutable
 * {@link RoleWithPermissions} projections.
 */
public interface RolePermissionCatalogPort {

    /**
     * Every {@code admin_roles} row with its permission-key set, ordered by
     * {@code admin_roles.id} ASC (the stable seed order that
     * {@code GET /api/admin/operators/grantable-roles} also uses). Each role's
     * {@code permissionKeys} list is sorted ascending for deterministic output.
     * Read-only.
     */
    List<RoleWithPermissions> findAllRolesWithPermissions();

    /**
     * Immutable projection of an {@code admin_roles} row plus its bound
     * permission keys ({@code admin_role_permissions.permission_key}).
     */
    record RoleWithPermissions(
            long id,
            String name,
            String description,
            List<String> permissionKeys
    ) {}
}

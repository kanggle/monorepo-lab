package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AdminRolePermissionJpaRepository
        extends JpaRepository<AdminRolePermissionJpaEntity, AdminRolePermissionJpaEntity.PK> {

    @Query("SELECT p.permissionKey FROM AdminRolePermissionJpaEntity p WHERE p.roleId IN :roleIds")
    List<String> findPermissionKeysByRoleIds(@Param("roleIds") Collection<Long> roleIds);

    /**
     * ADR-MONO-024 D2 — the subset of {@code roleIds} whose role grants
     * {@code permission}. Used by {@code AdminGrantScopeEvaluator} to compute an
     * operator's effective admin-grant scope for a permission: the {@code tenant_id}
     * of each {@code admin_operator_roles} row whose role appears in this result.
     */
    @Query("SELECT DISTINCT p.roleId FROM AdminRolePermissionJpaEntity p "
            + "WHERE p.permissionKey = :permission AND p.roleId IN :roleIds")
    List<Long> findRoleIdsGrantingPermission(@Param("permission") String permission,
                                             @Param("roleIds") Collection<Long> roleIds);

    /**
     * TASK-BE-486 — every {@code (role_id, permission_key)} binding for the given
     * roles, so {@code GET /api/admin/roles} can assemble each role's permission-key
     * set in a single query (no N+1). Ordering is implementation-defined; the
     * catalog adapter sorts the keys per role.
     */
    List<AdminRolePermissionJpaEntity> findByRoleIdIn(Collection<Long> roleIds);
}

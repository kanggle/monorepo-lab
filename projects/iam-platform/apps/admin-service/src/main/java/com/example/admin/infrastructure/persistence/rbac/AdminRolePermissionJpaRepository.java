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
}

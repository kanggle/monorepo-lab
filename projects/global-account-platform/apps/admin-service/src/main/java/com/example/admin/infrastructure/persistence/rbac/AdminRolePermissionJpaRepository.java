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
}

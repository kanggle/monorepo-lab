package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** TASK-BE-631 (V0052) — {@code domain_permission_set_role}. */
public interface DomainPermissionSetRoleJpaRepository
        extends JpaRepository<DomainPermissionSetRoleJpaEntity, DomainPermissionSetRoleJpaEntity.PK> {

    List<DomainPermissionSetRoleJpaEntity> findBySetIdIn(Collection<Long> setIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DomainPermissionSetRoleJpaEntity e WHERE e.setId = :setId")
    int deleteBySetId(@Param("setId") Long setId);
}

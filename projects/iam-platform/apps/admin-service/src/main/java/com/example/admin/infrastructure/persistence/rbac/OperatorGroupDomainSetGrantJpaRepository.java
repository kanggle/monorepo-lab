package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** TASK-BE-631 (V0052) — {@code operator_group_domain_set_grant} (read-only in slice A). */
public interface OperatorGroupDomainSetGrantJpaRepository
        extends JpaRepository<OperatorGroupDomainSetGrantJpaEntity, OperatorGroupDomainSetGrantJpaEntity.PK> {

    long countBySetId(Long setId);

    /** Groups of {@code tenantId} holding the set (a group's tenant is {@code operator_group.tenant_id}). */
    @Query("SELECT COUNT(g) FROM OperatorGroupDomainSetGrantJpaEntity g, OperatorGroupJpaEntity grp "
            + "WHERE g.groupId = grp.id AND g.setId = :setId AND grp.tenantId = :tenantId")
    long countBySetIdInTenant(@Param("setId") Long setId, @Param("tenantId") String tenantId);
}

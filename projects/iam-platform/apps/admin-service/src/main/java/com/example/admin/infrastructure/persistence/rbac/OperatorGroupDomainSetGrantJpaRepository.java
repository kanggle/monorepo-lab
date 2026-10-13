package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * TASK-BE-631 (V0052) — {@code operator_group_domain_set_grant}: the sets granted to a group (the templates its
 * fan-out materialises as {@code GROUP:<id>} rows of {@code operator_domain_set_grant}).
 */
public interface OperatorGroupDomainSetGrantJpaRepository
        extends JpaRepository<OperatorGroupDomainSetGrantJpaEntity, OperatorGroupDomainSetGrantJpaEntity.PK> {

    long countBySetId(Long setId);

    /** Groups of {@code tenantId} holding the set (a group's tenant is {@code operator_group.tenant_id}). */
    @Query("SELECT COUNT(g) FROM OperatorGroupDomainSetGrantJpaEntity g, OperatorGroupJpaEntity grp "
            + "WHERE g.groupId = grp.id AND g.setId = :setId AND grp.tenantId = :tenantId")
    long countBySetIdInTenant(@Param("setId") Long setId, @Param("tenantId") String tenantId);

    /** Slice B — the templates of one group, oldest first. */
    List<OperatorGroupDomainSetGrantJpaEntity> findByGroupIdOrderByGrantedAtAscSetIdAsc(Long groupId);

    /** Slice B — whether the group has at least one template (the member-add {@code domain_access.manage} gate). */
    boolean existsByGroupId(Long groupId);

    /** Slice B — the group set revoke: one template. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM OperatorGroupDomainSetGrantJpaEntity g WHERE g.groupId = :groupId AND g.setId = :setId")
    int deleteTemplate(@Param("groupId") Long groupId, @Param("setId") Long setId);

    /** Slice B — the group delete: every template of the group (the FK CASCADE is only the safety net). */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM OperatorGroupDomainSetGrantJpaEntity g WHERE g.groupId = :groupId")
    int deleteTemplatesOfGroup(@Param("groupId") Long groupId);
}

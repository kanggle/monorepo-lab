package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AdminOperatorRoleJpaRepository
        extends JpaRepository<AdminOperatorRoleJpaEntity, AdminOperatorRoleJpaEntity.PK> {

    List<AdminOperatorRoleJpaEntity> findByOperatorId(Long operatorId);

    /**
     * Bulk lookup for {@code GET /api/admin/operators} — fetches every role
     * binding for the returned page in one query.
     */
    List<AdminOperatorRoleJpaEntity> findByOperatorIdIn(Collection<Long> operatorIds);

    /** Delete every role binding for one operator (used on full role replacement). */
    @Modifying
    @Query("DELETE FROM AdminOperatorRoleJpaEntity e WHERE e.operatorId = :operatorId")
    int deleteByOperatorId(@Param("operatorId") Long operatorId);
}

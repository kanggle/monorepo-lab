package com.example.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** TASK-BE-491 (ADR-MONO-047 § D1): Spring Data access to {@code org_node}. */
public interface OrgNodeJpaRepository extends JpaRepository<OrgNodeJpaEntity, String> {

    List<OrgNodeJpaEntity> findByParentIdOrderByIdAsc(String parentId);

    boolean existsByParentId(String parentId);

    @Query("SELECT n FROM OrgNodeJpaEntity n ORDER BY n.depth ASC, n.id ASC")
    List<OrgNodeJpaEntity> findAllOrdered();
}

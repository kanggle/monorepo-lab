package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** TASK-BE-631 (V0052 · ADR-MONO-083 D4) — {@code operator_domain_set_grant}. */
public interface OperatorDomainSetGrantJpaRepository
        extends JpaRepository<OperatorDomainSetGrantJpaEntity, OperatorDomainSetGrantJpaEntity.PK> {

    List<OperatorDomainSetGrantJpaEntity> findByOperatorIdAndTenantIdIn(Long operatorId, Collection<String> tenantIds);

    List<OperatorDomainSetGrantJpaEntity> findByOperatorIdAndTenantIdAndSetIdAndSourceKeyIn(
            Long operatorId, String tenantId, Long setId, Collection<String> sourceKeys);

    boolean existsByOperatorIdAndTenantIdAndSetIdAndSourceKey(Long operatorId, String tenantId, Long setId,
                                                              String sourceKey);

    long countBySetIdAndTenantId(Long setId, String tenantId);

    long countBySetId(Long setId);

    /** Deletes the given sources' rows of (operator, tenant, set) — other sources' rows are untouched. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OperatorDomainSetGrantJpaEntity e WHERE e.operatorId = :operatorId AND e.tenantId = :tenantId "
            + "AND e.setId = :setId AND e.sourceKey IN :sourceKeys")
    int deleteSources(@Param("operatorId") Long operatorId, @Param("tenantId") String tenantId,
                      @Param("setId") Long setId, @Param("sourceKeys") Collection<String> sourceKeys);
}

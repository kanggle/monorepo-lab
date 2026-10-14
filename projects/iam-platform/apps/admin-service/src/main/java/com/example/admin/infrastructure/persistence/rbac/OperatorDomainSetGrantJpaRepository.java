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

    /**
     * TASK-BE-636 ② — whether the operator holds any set row in the tenant whose source is NOT {@code sourceKey}
     * (the «does a work grant still back this membership» check; the excluded source is the group being revoked,
     * whose own rows are about to go). Pass a key no row carries to count every source.
     */
    boolean existsByOperatorIdAndTenantIdAndSourceKeyNot(Long operatorId, String tenantId, String sourceKey);

    /** TASK-BE-636 ② — the tenants the operator holds at least one set row in (any source). */
    @Query("SELECT DISTINCT e.tenantId FROM OperatorDomainSetGrantJpaEntity e WHERE e.operatorId = :operatorId")
    List<String> findTenantIdsOfOperator(@Param("operatorId") Long operatorId);

    long countBySetIdAndTenantId(Long setId, String tenantId);

    long countBySetId(Long setId);

    /** Deletes the given sources' rows of (operator, tenant, set) — other sources' rows are untouched. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM OperatorDomainSetGrantJpaEntity e WHERE e.operatorId = :operatorId AND e.tenantId = :tenantId "
            + "AND e.setId = :setId AND e.sourceKey IN :sourceKeys")
    int deleteSources(@Param("operatorId") Long operatorId, @Param("tenantId") String tenantId,
                      @Param("setId") Long setId, @Param("sourceKeys") Collection<String> sourceKeys);

    // ---- slice B: group-source deletes — always keyed on the exact source_key 'GROUP:<id>' (group_origin is the
    // index that finds the rows; the source_key predicate is what makes the delete correct) ----

    /** The group set revoke: that group's rows of one set, every member. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM OperatorDomainSetGrantJpaEntity e WHERE e.groupOrigin = :groupId "
            + "AND e.sourceKey = :sourceKey AND e.setId = :setId")
    int deleteGroupSourceOfSet(@Param("groupId") Long groupId, @Param("sourceKey") String sourceKey,
                               @Param("setId") Long setId);

    /** The member removal: that group's rows of one member, every set. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM OperatorDomainSetGrantJpaEntity e WHERE e.groupOrigin = :groupId "
            + "AND e.sourceKey = :sourceKey AND e.operatorId = :operatorId")
    int deleteGroupSourceOfOperator(@Param("groupId") Long groupId, @Param("sourceKey") String sourceKey,
                                    @Param("operatorId") Long operatorId);

    /** The group delete: every row the group produced. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM OperatorDomainSetGrantJpaEntity e WHERE e.groupOrigin = :groupId AND e.sourceKey = :sourceKey")
    int deleteGroupSource(@Param("groupId") Long groupId, @Param("sourceKey") String sourceKey);

    /** «이 회사에서 내보내기»: every row of (operator, tenant) — EVERY source. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM OperatorDomainSetGrantJpaEntity e WHERE e.operatorId = :operatorId AND e.tenantId = :tenantId")
    int deleteAllOfOperatorInTenant(@Param("operatorId") Long operatorId, @Param("tenantId") String tenantId);
}

package com.example.admin.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * TASK-BE-477 / ADR-MONO-045 D4 — Spring Data repository over
 * {@code tenant_partnership_participant}.
 */
public interface TenantPartnershipParticipantJpaRepository
        extends JpaRepository<TenantPartnershipParticipantJpaEntity, TenantPartnershipParticipantJpaEntity.PK> {

    Optional<TenantPartnershipParticipantJpaEntity> findByPartnershipIdAndOperatorId(
            Long partnershipId, Long operatorId);

    int countByPartnershipId(Long partnershipId);

    /**
     * TASK-MONO-784 S4 (D-9 «이 회사에서 내보내기» · 784 AC-0 F18) — one operator's participant bindings in the
     * partnerships whose partner is {@code partnerTenantId}, any partnership status.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TenantPartnershipParticipantJpaEntity p WHERE p.operatorId = :operatorId "
            + "AND p.partnershipId IN (SELECT t.id FROM TenantPartnershipJpaEntity t "
            + "WHERE t.partnerTenantId = :partnerTenantId)")
    int deleteByOperatorIdAndPartnerTenantId(@Param("operatorId") Long operatorId,
                                             @Param("partnerTenantId") String partnerTenantId);
}

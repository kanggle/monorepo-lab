package com.example.admin.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

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
}

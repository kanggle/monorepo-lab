package com.example.account.infrastructure.persistence;

import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** TASK-BE-614 — adapter for {@link ConsumerSiteMembershipRepository}. */
@Repository
@RequiredArgsConstructor
public class ConsumerSiteMembershipRepositoryImpl implements ConsumerSiteMembershipRepository {

    private final ConsumerSiteMembershipJpaRepository jpaRepository;

    @Override
    public void insert(ConsumerSiteMembership membership) {
        jpaRepository.insertMembership(
                membership.getAccountId(),
                membership.getSiteTenantId().value(),
                membership.getStatus().name(),
                membership.getConsentedAt());
    }

    @Override
    public Optional<ConsumerSiteMembership> find(TenantId siteTenantId, String accountId) {
        return jpaRepository.findBySiteTenantIdAndAccountId(siteTenantId.value(), accountId)
                .map(ConsumerSiteMembershipJpaEntity::toDomain);
    }

    @Override
    public List<String> findSiteRoles(TenantId siteTenantId, String accountId) {
        return jpaRepository.findSiteRoleNames(siteTenantId.value(), accountId);
    }
}

package com.example.account.infrastructure.persistence;

import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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
    public boolean update(ConsumerSiteMembership membership) {
        return jpaRepository.updateMembership(
                membership.getSiteTenantId().value(),
                membership.getAccountId(),
                membership.getStatus().name(),
                membership.getConsentedAt(),
                membership.getLeftAt(),
                membership.getLeftBy() == null ? null : membership.getLeftBy().name(),
                membership.getLeftByActorId()) > 0;
    }

    @Override
    public int removeAllSiteRoles(TenantId siteTenantId, String accountId) {
        return jpaRepository.deleteAllSiteRoles(siteTenantId.value(), accountId);
    }

    @Override
    public List<String> findSiteRoles(TenantId siteTenantId, String accountId) {
        return jpaRepository.findSiteRoleNames(siteTenantId.value(), accountId);
    }

    @Override
    public void addSiteRole(TenantId siteTenantId, String accountId, String roleName, String grantedBy,
                            Instant grantedAt) {
        jpaRepository.insertSiteRole(accountId, siteTenantId.value(), roleName, grantedBy, grantedAt);
    }

    @Override
    public boolean removeSiteRole(TenantId siteTenantId, String accountId, String roleName) {
        return jpaRepository.deleteSiteRole(siteTenantId.value(), accountId, roleName) > 0;
    }
}

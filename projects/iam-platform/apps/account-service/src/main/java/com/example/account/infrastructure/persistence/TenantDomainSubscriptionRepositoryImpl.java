package com.example.account.infrastructure.persistence;

import com.example.account.domain.repository.TenantDomainSubscriptionRepository;
import com.example.account.domain.tenant.SubscriptionStatus;
import com.example.account.domain.tenant.TenantDomainSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TenantDomainSubscriptionRepositoryImpl implements TenantDomainSubscriptionRepository {

    private final TenantDomainSubscriptionJpaRepository jpaRepository;

    @Override
    public List<TenantDomainSubscription> findAllActive() {
        return jpaRepository.findByStatus(SubscriptionStatus.ACTIVE).stream()
                .map(TenantDomainSubscriptionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<TenantDomainSubscription> findActiveByTenantId(String tenantId) {
        return jpaRepository.findByStatusAndTenantId(SubscriptionStatus.ACTIVE, tenantId).stream()
                .map(TenantDomainSubscriptionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<TenantDomainSubscription> findByTenantIdAndDomainKey(String tenantId, String domainKey) {
        return jpaRepository.findByTenantIdAndDomainKey(tenantId, domainKey)
                .map(TenantDomainSubscriptionJpaEntity::toDomain);
    }

    @Override
    public TenantDomainSubscription save(TenantDomainSubscription subscription) {
        return jpaRepository.save(TenantDomainSubscriptionJpaEntity.fromDomain(subscription)).toDomain();
    }
}

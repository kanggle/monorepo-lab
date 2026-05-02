package com.example.account.infrastructure.persistence;

import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TenantJpaAdapter implements TenantRepository {

    private final TenantJpaRepository jpaRepository;

    @Override
    public Optional<Tenant> findById(TenantId tenantId) {
        return jpaRepository.findById(tenantId.value())
                .map(TenantJpaEntity::toDomain);
    }

    @Override
    public boolean existsActive(TenantId tenantId) {
        return jpaRepository.existsByTenantIdAndStatus(tenantId.value(), TenantStatus.ACTIVE);
    }

    @Override
    public boolean existsById(TenantId tenantId) {
        return jpaRepository.existsById(tenantId.value());
    }

    @Override
    public Tenant save(Tenant tenant) {
        TenantJpaEntity entity = TenantJpaEntity.fromDomain(tenant);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Page<Tenant> findAll(TenantStatus statusFilter, TenantType tenantTypeFilter, Pageable pageable) {
        return jpaRepository.findAllFiltered(statusFilter, tenantTypeFilter, pageable)
                .map(TenantJpaEntity::toDomain);
    }
}

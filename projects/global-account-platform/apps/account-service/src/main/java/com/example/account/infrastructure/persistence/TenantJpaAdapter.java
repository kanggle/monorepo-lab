package com.example.account.infrastructure.persistence;

import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import lombok.RequiredArgsConstructor;
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
}

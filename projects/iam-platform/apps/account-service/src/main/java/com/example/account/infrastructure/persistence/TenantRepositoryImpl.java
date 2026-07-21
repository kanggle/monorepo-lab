package com.example.account.infrastructure.persistence;

import com.example.account.domain.orgnode.OrgNodeId;
import com.example.account.domain.repository.PageResult;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TenantRepositoryImpl implements TenantRepository {

    private final TenantJpaRepository jpaRepository;

    @Override
    public Optional<Tenant> findById(TenantId tenantId) {
        return jpaRepository.findById(tenantId.value())
                .map(TenantJpaEntity::toDomain);
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
    public PageResult<Tenant> findAll(TenantStatus statusFilter, TenantType tenantTypeFilter, int page, int size) {
        Page<TenantJpaEntity> jpaPage = jpaRepository.findAllFiltered(
                statusFilter, tenantTypeFilter, PageRequest.of(page, size));
        return new PageResult<>(
                jpaPage.getContent().stream().map(TenantJpaEntity::toDomain).toList(),
                jpaPage.getTotalElements(),
                jpaPage.getNumber(),
                jpaPage.getSize(),
                jpaPage.getTotalPages()
        );
    }

    @Override
    public List<String> findTenantIdsByOrgNodeIdIn(List<String> orgNodeIds) {
        // An empty IN (...) list is invalid SQL on some engines, and semantically matches
        // nothing anyway — short-circuit rather than issue the query.
        if (orgNodeIds == null || orgNodeIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findTenantIdsByOrgNodeIdIn(orgNodeIds);
    }

    @Override
    public long countByOrgNodeId(OrgNodeId orgNodeId) {
        return jpaRepository.countByOrgNodeId(orgNodeId.value());
    }
}

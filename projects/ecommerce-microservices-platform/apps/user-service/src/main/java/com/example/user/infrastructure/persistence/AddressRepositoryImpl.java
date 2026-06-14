package com.example.user.infrastructure.persistence;

import com.example.user.domain.model.Address;
import com.example.user.domain.repository.AddressRepository;
import com.example.user.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class AddressRepositoryImpl implements AddressRepository {

    private final AddressJpaRepository jpaRepository;
    private final AddressJpaMapper mapper;

    @Override
    public Address save(Address address) {
        AddressJpaEntity entity = mapper.toEntity(address);
        AddressJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Address> findByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.findByIdAndUserIdAndTenantId(id, userId, TenantContext.currentTenant()).map(mapper::toDomain);
    }

    @Override
    public List<Address> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserIdAndTenantId(userId, TenantContext.currentTenant()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public int countByUserId(UUID userId) {
        return jpaRepository.countByUserIdAndTenantId(userId, TenantContext.currentTenant());
    }

    @Override
    public void delete(Address address) {
        jpaRepository.findById(address.getId())
                .ifPresent(jpaRepository::delete);
    }

    @Override
    public void unmarkDefaultByUserId(UUID userId) {
        jpaRepository.unmarkDefaultByUserId(userId, TenantContext.currentTenant());
    }
}

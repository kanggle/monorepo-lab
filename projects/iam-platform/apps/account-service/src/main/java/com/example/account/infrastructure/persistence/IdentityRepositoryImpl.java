package com.example.account.infrastructure.persistence;

import com.example.account.domain.identity.Identity;
import com.example.account.domain.repository.IdentityRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class IdentityRepositoryImpl implements IdentityRepository {

    private final IdentityJpaRepository jpaRepository;

    @Override
    public Identity save(Identity identity) {
        IdentityJpaEntity saved = jpaRepository.save(IdentityJpaEntity.fromDomain(identity));
        return saved.toDomain();
    }

    @Override
    public Optional<Identity> findById(String identityId) {
        return jpaRepository.findById(identityId).map(IdentityJpaEntity::toDomain);
    }

    @Override
    public Optional<Identity> findByTenantAndEmail(TenantId tenantId, String email) {
        return jpaRepository.findByTenantIdAndPrimaryEmail(tenantId.value(), email)
                .map(IdentityJpaEntity::toDomain);
    }
}

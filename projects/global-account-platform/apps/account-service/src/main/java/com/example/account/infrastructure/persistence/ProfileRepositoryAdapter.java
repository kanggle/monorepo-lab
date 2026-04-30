package com.example.account.infrastructure.persistence;

import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProfileRepositoryAdapter implements ProfileRepository {

    private final ProfileJpaRepository jpaRepository;

    @Override
    public Profile save(Profile profile) {
        ProfileJpaEntity entity = ProfileJpaEntity.fromDomain(profile);
        ProfileJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    /**
     * TASK-BE-231: Save a profile with an explicit tenantId.
     * Used by the provisioning flow for non-default tenants.
     */
    @Override
    public Profile save(Profile profile, TenantId tenantId) {
        ProfileJpaEntity entity = ProfileJpaEntity.fromDomain(profile, tenantId.value());
        ProfileJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Profile> findByAccountId(String accountId) {
        return jpaRepository.findByAccountId(accountId).map(ProfileJpaEntity::toDomain);
    }
}

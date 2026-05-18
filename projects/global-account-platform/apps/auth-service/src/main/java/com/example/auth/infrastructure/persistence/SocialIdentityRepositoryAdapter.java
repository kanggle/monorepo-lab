package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.repository.SocialIdentityRepository;
import com.example.auth.domain.social.SocialIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SocialIdentityRepositoryAdapter implements SocialIdentityRepository {

    private final SocialIdentityJpaRepository socialIdentityJpaRepository;

    @Override
    public Optional<SocialIdentity> findByProviderAndProviderUserId(String provider, String providerUserId) {
        return socialIdentityJpaRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(SocialIdentityJpaEntity::toDomain);
    }

    @Override
    public SocialIdentity save(SocialIdentity socialIdentity) {
        SocialIdentityJpaEntity entity = SocialIdentityJpaEntity.fromDomain(socialIdentity);
        return socialIdentityJpaRepository.save(entity).toDomain();
    }
}

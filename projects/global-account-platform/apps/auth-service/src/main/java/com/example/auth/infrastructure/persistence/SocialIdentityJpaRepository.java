package com.example.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialIdentityJpaRepository extends JpaRepository<SocialIdentityJpaEntity, Long> {

    Optional<SocialIdentityJpaEntity> findByProviderAndProviderUserId(String provider, String providerUserId);

    List<SocialIdentityJpaEntity> findByAccountId(String accountId);
}

package com.example.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialIdentityJpaRepository extends JpaRepository<SocialIdentityJpaEntity, Long> {

    // TASK-BE-611: the same columns as the unique index uk_social_tenant_provider_user (V0007).
    Optional<SocialIdentityJpaEntity> findByTenantIdAndProviderAndProviderUserId(
            String tenantId, String provider, String providerUserId);

    List<SocialIdentityJpaEntity> findByAccountId(String accountId);
}

package com.example.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdentityJpaRepository extends JpaRepository<IdentityJpaEntity, String> {

    Optional<IdentityJpaEntity> findByTenantIdAndPrimaryEmail(String tenantId, String primaryEmail);
}

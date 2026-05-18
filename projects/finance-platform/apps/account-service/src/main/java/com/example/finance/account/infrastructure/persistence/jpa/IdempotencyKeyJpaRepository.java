package com.example.finance.account.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyJpaRepository
        extends JpaRepository<IdempotencyKeyJpaEntity, IdempotencyKeyJpaEntity.Pk> {

    Optional<IdempotencyKeyJpaEntity> findByIdempotencyKeyAndEndpointAndTenantId(
            String idempotencyKey, String endpoint, String tenantId);
}

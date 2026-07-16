package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link IdempotencyKeyJpaEntity} (TASK-BE-445).
 * Picked up by the application's {@code @EnableJpaRepositories(basePackages =
 * "…infrastructure.persistence.jpa")}.
 */
public interface IdempotencyKeyJpaRepository
        extends JpaRepository<IdempotencyKeyJpaEntity, IdempotencyKeyJpaEntity.Id> {
}

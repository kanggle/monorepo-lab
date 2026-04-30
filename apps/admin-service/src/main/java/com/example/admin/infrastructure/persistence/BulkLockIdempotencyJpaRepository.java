package com.example.admin.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkLockIdempotencyJpaRepository
        extends JpaRepository<BulkLockIdempotencyJpaEntity, BulkLockIdempotencyJpaEntity.Key> {
}

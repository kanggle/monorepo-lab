package com.example.user.infrastructure.persistence;

import com.example.user.domain.model.ProfileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, UUID> {

    Optional<UserProfileJpaEntity> findByUserIdAndTenantId(UUID userId, String tenantId);

    // Tenant-agnostic on purpose (ADR-MONO-030 Step 4, M1; TASK-BE-367): the
    // account.created onboarding dedup path keys off user_id (= IAM accountId), which is
    // globally unique (uq_user_profiles_user_id) — an IAM account belongs to one tenant.
    // Mirrors order-service findByIdAcrossTenants: identity dedup must not be tenant-scoped.
    boolean existsByUserId(UUID userId);

    Page<UserProfileJpaEntity> findByTenantIdAndStatusAndEmailContaining(String tenantId, ProfileStatus status, String email, Pageable pageable);

    Page<UserProfileJpaEntity> findByTenantIdAndStatus(String tenantId, ProfileStatus status, Pageable pageable);

    Page<UserProfileJpaEntity> findByTenantIdAndEmailContaining(String tenantId, String email, Pageable pageable);

    Page<UserProfileJpaEntity> findByTenantId(String tenantId, Pageable pageable);

    long countByTenantId(String tenantId);

    long countByTenantIdAndCreatedAtBetween(String tenantId, Instant from, Instant to);
}

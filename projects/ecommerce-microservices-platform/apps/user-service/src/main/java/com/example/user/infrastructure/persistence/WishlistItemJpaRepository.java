package com.example.user.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface WishlistItemJpaRepository extends JpaRepository<WishlistItemJpaEntity, UUID> {

    boolean existsByUserIdAndProductIdAndTenantId(UUID userId, UUID productId, String tenantId);

    Optional<WishlistItemJpaEntity> findByUserIdAndProductIdAndTenantId(UUID userId, UUID productId, String tenantId);

    Page<WishlistItemJpaEntity> findAllByUserIdAndTenantId(UUID userId, String tenantId, Pageable pageable);

    Optional<WishlistItemJpaEntity> findByIdAndTenantId(UUID id, String tenantId);

    void deleteAllByUserIdAndTenantId(UUID userId, String tenantId);
}

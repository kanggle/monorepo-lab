package com.example.user.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface WishlistItemJpaRepository extends JpaRepository<WishlistItemJpaEntity, UUID> {

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    Optional<WishlistItemJpaEntity> findByUserIdAndProductId(UUID userId, UUID productId);

    Page<WishlistItemJpaEntity> findAllByUserId(UUID userId, Pageable pageable);

    void deleteAllByUserId(UUID userId);
}

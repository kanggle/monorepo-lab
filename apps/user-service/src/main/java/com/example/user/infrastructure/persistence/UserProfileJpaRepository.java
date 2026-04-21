package com.example.user.infrastructure.persistence;

import com.example.user.domain.model.ProfileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, UUID> {

    Optional<UserProfileJpaEntity> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    Page<UserProfileJpaEntity> findByStatusAndEmailContaining(ProfileStatus status, String email, Pageable pageable);

    Page<UserProfileJpaEntity> findByStatus(ProfileStatus status, Pageable pageable);

    Page<UserProfileJpaEntity> findByEmailContaining(String email, Pageable pageable);
}

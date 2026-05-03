package com.example.fanplatform.artist.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ArtistGroupJpaRepository extends JpaRepository<ArtistGroupJpaEntity, String> {

    Optional<ArtistGroupJpaEntity> findByIdAndTenantId(String id, String tenantId);

    boolean existsByTenantIdAndName(String tenantId, String name);
}

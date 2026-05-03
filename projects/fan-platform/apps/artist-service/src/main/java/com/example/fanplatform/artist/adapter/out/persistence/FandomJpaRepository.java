package com.example.fanplatform.artist.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface FandomJpaRepository extends JpaRepository<FandomJpaEntity, String> {

    Optional<FandomJpaEntity> findByArtistIdAndTenantId(String artistId, String tenantId);

    boolean existsByArtistIdAndTenantId(String artistId, String tenantId);
}

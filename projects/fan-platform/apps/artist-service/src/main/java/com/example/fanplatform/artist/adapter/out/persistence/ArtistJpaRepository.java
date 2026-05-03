package com.example.fanplatform.artist.adapter.out.persistence;

import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface ArtistJpaRepository extends JpaRepository<ArtistJpaEntity, String> {

    Optional<ArtistJpaEntity> findByIdAndTenantId(String id, String tenantId);

    boolean existsByTenantIdAndStageName(String tenantId, String stageName);

    boolean existsByIdAndTenantIdAndStatus(String id, String tenantId, ArtistStatus status);

    /**
     * Directory page query. Returns only PUBLISHED rows in the given tenant.
     * Optional case-insensitive substring match on stage_name. Optional
     * artistType filter. Sorted at the {@link Pageable} level (default
     * stage_name asc).
     */
    @Query("""
            SELECT a FROM ArtistJpaEntity a
            WHERE a.tenantId = :tenantId
              AND a.status = com.example.fanplatform.artist.domain.artist.ArtistStatus.PUBLISHED
              AND (:q IS NULL OR LOWER(a.stageName) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:type IS NULL OR a.artistType = :type)
            """)
    Page<ArtistJpaEntity> searchPublished(@Param("tenantId") String tenantId,
                                          @Param("q") String q,
                                          @Param("type") ArtistType type,
                                          Pageable pageable);
}

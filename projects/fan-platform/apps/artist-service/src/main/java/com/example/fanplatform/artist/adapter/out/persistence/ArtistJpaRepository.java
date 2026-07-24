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
     *
     * <p>{@code CAST(:q AS string)} on BOTH occurrences of the nullable search
     * term is load-bearing. A bare {@code :q} binds an UNTYPED parameter; when
     * {@code q} is null (empty search), PostgreSQL cannot plan the
     * {@code LOWER(('%'||?||'%'))} subtree and resolves the untyped-null
     * argument of {@code lower(...)} to the non-existent {@code lower(bytea)}
     * overload → {@code 42883 function lower(bytea) does not exist} → HTTP 500
     * on the default "browse all artists" page. Hibernate binds {@code :q}
     * twice (the {@code IS NULL} guard and the {@code CONCAT}), so both must be
     * cast for PostgreSQL to type the parameter. Same idiom as the wms-platform
     * optional-filter repositories.
     */
    @Query("""
            SELECT a FROM ArtistJpaEntity a
            WHERE a.tenantId = :tenantId
              AND a.status = com.example.fanplatform.artist.domain.artist.ArtistStatus.PUBLISHED
              AND (CAST(:q AS string) IS NULL OR LOWER(a.stageName) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
              AND (:type IS NULL OR a.artistType = :type)
            """)
    Page<ArtistJpaEntity> searchPublished(@Param("tenantId") String tenantId,
                                          @Param("q") String q,
                                          @Param("type") ArtistType type,
                                          Pageable pageable);
}

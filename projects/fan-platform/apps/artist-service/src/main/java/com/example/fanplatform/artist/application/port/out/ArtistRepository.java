package com.example.fanplatform.artist.application.port.out;

import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.fanplatform.artist.domain.artist.ArtistType;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for artist persistence. Adapter under
 * {@code adapter.out.persistence}. Domain types only — no JPA / Spring Data
 * leakage into the application layer (Hexagonal purity).
 */
public interface ArtistRepository {

    /**
     * Persist a brand-new artist. Throws
     * {@link com.example.fanplatform.artist.application.exception.StageNameConflictException}
     * when {@code (tenant_id, stage_name)} collides.
     */
    Artist insert(Artist artist);

    /**
     * Persist mutable changes. Optimistic-lock conflicts surface as
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.
     */
    Artist update(Artist artist);

    Optional<Artist> findById(ArtistId id, String tenantId);

    /**
     * Cross-tenant lookup used by background jobs / admin tools. v1 application
     * code MUST always go through {@link #findById(ArtistId, String)}; this
     * variant is exposed for symmetry with the wms master-service pattern.
     */
    Optional<Artist> findByIdRaw(ArtistId id);

    /**
     * Directory page for the {@code SearchArtistDirectoryUseCase}. Returns
     * only PUBLISHED artists in the given tenant. Sorted by stage_name.
     */
    DirectoryPage findPublishedDirectoryPage(String tenantId, String q, ArtistType type,
                                             int page, int size);

    boolean existsByTenantIdAndStageName(String tenantId, String stageName);

    /** True if the artist exists in this tenant with the given status. */
    boolean existsInStatus(ArtistId id, String tenantId, ArtistStatus status);

    record DirectoryPage(
            List<Artist> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}

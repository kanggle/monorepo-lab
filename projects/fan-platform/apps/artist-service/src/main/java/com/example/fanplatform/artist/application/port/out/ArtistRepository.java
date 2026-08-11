package com.example.fanplatform.artist.application.port.out;

import com.example.common.page.PageResult;
import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.fanplatform.artist.domain.artist.ArtistType;

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
    PageResult<Artist> findPublishedDirectoryPage(String tenantId, String q, ArtistType type,
                                                   int page, int size);

    boolean existsByTenantIdAndStageName(String tenantId, String stageName);

    /**
     * True iff some artist in {@code tenantId} is authored by {@code accountId}.
     * Two callers: the registration pre-check for 409
     * {@code ARTIST_ACCOUNT_CONFLICT}, and {@code GET /internal/artists/exists},
     * the remote half of community-service's follow-target validation
     * (TASK-FAN-BE-045 AC-6, ADR-004 A).
     */
    boolean existsByTenantIdAndAccountId(String tenantId, String accountId);

    /** True if the artist exists in this tenant with the given status. */
    boolean existsInStatus(ArtistId id, String tenantId, ArtistStatus status);
}

package com.example.fanplatform.artist.adapter.out.persistence;

import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.artist.ArtistProfile;
import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter for {@link ArtistRepository}. {@code @Repository} so
 * Hibernate {@code ConstraintViolationException} surfaces as
 * {@code DataIntegrityViolationException} for the unique-constraint guard.
 */
@Repository
class ArtistRepositoryImpl implements ArtistRepository {

    private final ArtistJpaRepository jpa;

    ArtistRepositoryImpl(ArtistJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Artist insert(Artist artist) {
        ArtistJpaEntity entity = toInsertEntity(artist);
        ArtistJpaEntity saved = jpa.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    public Artist update(Artist artist) {
        ArtistJpaEntity managed = jpa.findById(artist.getId().value())
                .orElseThrow(() -> new IllegalStateException(
                        "Artist disappeared between load and save: " + artist.getId().value()));
        ArtistProfile p = artist.getProfile();
        managed.applyMutable(
                artist.getStatus(),
                p.stageName(), p.realName(), p.debutDate(), p.agency(), p.bio(), p.profileImageRef(),
                artist.getUpdatedAt(),
                artist.getPublishedAt(),
                artist.getArchivedAt());
        ArtistJpaEntity saved = jpa.saveAndFlush(managed);
        return toDomain(saved);
    }

    @Override
    public Optional<Artist> findById(ArtistId id, String tenantId) {
        return jpa.findByIdAndTenantId(id.value(), tenantId).map(this::toDomain);
    }

    @Override
    public Optional<Artist> findByIdRaw(ArtistId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public DirectoryPage findPublishedDirectoryPage(String tenantId, String q, ArtistType type,
                                                    int page, int size) {
        // Stable, deterministic ordering for cache hashing: stage_name asc, id asc as tiebreaker.
        Sort sort = Sort.by(Sort.Direction.ASC, "stageName").and(Sort.by(Sort.Direction.ASC, "id"));
        Page<ArtistJpaEntity> p = jpa.searchPublished(tenantId, q, type, PageRequest.of(page, size, sort));
        List<Artist> items = p.getContent().stream().map(this::toDomain).toList();
        return new DirectoryPage(items, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Override
    public boolean existsByTenantIdAndStageName(String tenantId, String stageName) {
        return jpa.existsByTenantIdAndStageName(tenantId, stageName);
    }

    @Override
    public boolean existsInStatus(ArtistId id, String tenantId, ArtistStatus status) {
        return jpa.existsByIdAndTenantIdAndStatus(id.value(), tenantId, status);
    }

    private ArtistJpaEntity toInsertEntity(Artist a) {
        ArtistProfile p = a.getProfile();
        return new ArtistJpaEntity(
                a.getId().value(), a.getTenantId(), a.getArtistType(), a.getStatus(),
                p.stageName(), p.realName(), p.debutDate(), p.agency(), p.bio(), p.profileImageRef(),
                a.getCreatedAt(), a.getUpdatedAt(), a.getPublishedAt(), a.getArchivedAt(),
                // version=null on insert so Spring Data treats this as new and runs INSERT.
                null
        );
    }

    private Artist toDomain(ArtistJpaEntity e) {
        ArtistProfile profile = new ArtistProfile(
                e.getStageName(), e.getRealName(), e.getDebutDate(),
                e.getAgency(), e.getBio(), e.getProfileImageRef());
        return Artist.reconstitute(
                ArtistId.of(e.getId()),
                e.getTenantId(),
                e.getArtistType(),
                e.getStatus(),
                profile,
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getPublishedAt(),
                e.getArchivedAt(),
                e.getVersion() == null ? 0L : e.getVersion()
        );
    }

}

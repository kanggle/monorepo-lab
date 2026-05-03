package com.example.fanplatform.artist.adapter.out.persistence;

import com.example.fanplatform.artist.application.port.out.FandomRepository;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.fandom.Fandom;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class FandomRepositoryAdapter implements FandomRepository {

    private final FandomJpaRepository jpa;

    FandomRepositoryAdapter(FandomJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Fandom insert(Fandom f) {
        FandomJpaEntity entity = new FandomJpaEntity(
                f.getArtistId().value(), f.getTenantId(), f.getFandomName(), f.getColorHex(),
                f.getFoundedAt(), f.getSlogan(), f.getCreatedAt(), f.getUpdatedAt(),
                null /* insert */);
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public Fandom update(Fandom f) {
        FandomJpaEntity managed = jpa.findById(f.getArtistId().value())
                .orElseThrow(() -> new IllegalStateException(
                        "Fandom disappeared between load and save: " + f.getArtistId().value()));
        managed.applyMutable(f.getFandomName(), f.getColorHex(), f.getFoundedAt(), f.getSlogan(),
                f.getUpdatedAt());
        return toDomain(jpa.saveAndFlush(managed));
    }

    @Override
    public Optional<Fandom> findByArtistId(ArtistId artistId, String tenantId) {
        return jpa.findByArtistIdAndTenantId(artistId.value(), tenantId).map(this::toDomain);
    }

    @Override
    public boolean existsByArtistId(ArtistId artistId, String tenantId) {
        return jpa.existsByArtistIdAndTenantId(artistId.value(), tenantId);
    }

    private Fandom toDomain(FandomJpaEntity e) {
        return Fandom.reconstitute(
                ArtistId.of(e.getArtistId()),
                e.getTenantId(),
                e.getFandomName(), e.getColorHex(), e.getFoundedAt(), e.getSlogan(),
                e.getCreatedAt(), e.getUpdatedAt(),
                e.getVersion() == null ? 0L : e.getVersion()
        );
    }
}

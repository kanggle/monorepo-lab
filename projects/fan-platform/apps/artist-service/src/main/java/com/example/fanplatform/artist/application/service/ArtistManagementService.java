package com.example.fanplatform.artist.application.service;

import com.example.common.id.UuidV7;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.exception.AdminRoleRequiredException;
import com.example.fanplatform.artist.application.exception.ArtistNotFoundException;
import com.example.fanplatform.artist.application.exception.StageNameConflictException;
import com.example.fanplatform.artist.application.port.in.ArchiveArtistUseCase;
import com.example.fanplatform.artist.application.port.in.ArtistView;
import com.example.fanplatform.artist.application.port.in.GetArtistUseCase;
import com.example.fanplatform.artist.application.port.in.PublishArtistUseCase;
import com.example.fanplatform.artist.application.port.in.RegisterArtistUseCase;
import com.example.fanplatform.artist.application.port.in.UpdateArtistUseCase;
import com.example.fanplatform.artist.application.port.out.ArtistDirectoryCache;
import com.example.fanplatform.artist.application.port.out.ArtistEventPublisher;
import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.artist.ArtistProfile;
import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service implementing register / update / publish / archive /
 * get for {@code Artist}.
 *
 * <p>All mutating use cases require admin role (defense-in-depth — Spring
 * Security also blocks via {@code SecurityConfig}). Mutations happen inside a
 * single transaction with the outbox INSERT (transactional.md T3).
 */
@Service
@RequiredArgsConstructor
public class ArtistManagementService implements
        RegisterArtistUseCase,
        UpdateArtistUseCase,
        PublishArtistUseCase,
        ArchiveArtistUseCase,
        GetArtistUseCase {

    private final ArtistRepository artistRepository;
    private final ArtistEventPublisher eventPublisher;
    private final ArtistDirectoryCache directoryCache;

    @Override
    @Transactional
    public ArtistView register(RegisterArtistCommand cmd) {
        requireAdmin(cmd.actor());
        String tenantId = cmd.actor().tenantId();
        // Pre-check stage_name uniqueness so we can return a clean 409 envelope
        // before the unique constraint fires (the constraint is the canonical
        // guard for races).
        if (artistRepository.existsByTenantIdAndStageName(tenantId, cmd.stageName())) {
            throw new StageNameConflictException(cmd.stageName());
        }
        ArtistProfile profile = new ArtistProfile(
                cmd.stageName(), cmd.realName(), cmd.debutDate(),
                cmd.agency(), cmd.bio(), cmd.profileImageRef());
        Artist artist = Artist.register(
                ArtistId.of(UuidV7.randomString()),
                tenantId,
                cmd.artistType(),
                profile);
        Artist saved = artistRepository.insert(artist);
        eventPublisher.publishArtistRegistered(saved, cmd.actor().accountId());
        // DRAFT artists do not appear in the public directory, so cache
        // invalidation is unnecessary on register. Publish/update/archive
        // handle invalidation downstream.
        return ArtistView.from(saved);
    }

    @Override
    @Transactional
    public ArtistView update(UpdateArtistCommand cmd) {
        requireAdmin(cmd.actor());
        String tenantId = cmd.actor().tenantId();
        Artist artist = loadOrThrow(cmd.artistId(), tenantId);

        ArtistProfile current = artist.getProfile();
        ArtistProfile next = current;
        if (cmd.stageName() != null && !cmd.stageName().equals(current.stageName())) {
            if (artistRepository.existsByTenantIdAndStageName(tenantId, cmd.stageName())) {
                throw new StageNameConflictException(cmd.stageName());
            }
            next = next.withStageName(cmd.stageName());
        }
        if (cmd.realName() != null) next = next.withRealName(cmd.realName());
        if (cmd.debutDate() != null) next = next.withDebutDate(cmd.debutDate());
        if (cmd.agency() != null) next = next.withAgency(cmd.agency());
        if (cmd.bio() != null) next = next.withBio(cmd.bio());
        if (cmd.profileImageRef() != null) next = next.withProfileImageRef(cmd.profileImageRef());

        artist.updateProfile(next);
        Artist saved = artistRepository.update(artist);

        List<String> changedFields = cmd.changedFields();
        if (!changedFields.isEmpty()) {
            eventPublisher.publishArtistUpdated(
                    saved.getId(), saved.getTenantId(),
                    changedFields, cmd.actor().accountId(), Instant.now());
        }
        if (saved.isPublished()) {
            directoryCache.invalidateAll(tenantId);
        }
        return ArtistView.from(saved);
    }

    @Override
    @Transactional
    public ArtistView publish(ActorContext actor, String artistId) {
        requireAdmin(actor);
        Artist artist = loadOrThrow(artistId, actor.tenantId());
        artist.publish();
        Artist saved = artistRepository.update(artist);
        eventPublisher.publishArtistPublished(saved);
        directoryCache.invalidateAll(actor.tenantId());
        return ArtistView.from(saved);
    }

    @Override
    @Transactional
    public ArtistView archive(ActorContext actor, String artistId, String reason) {
        requireAdmin(actor);
        Artist artist = loadOrThrow(artistId, actor.tenantId());
        boolean wasPublished = artist.isPublished();
        artist.archive();
        Artist saved = artistRepository.update(artist);
        eventPublisher.publishArtistArchived(saved, reason, actor.accountId());
        if (wasPublished) {
            directoryCache.invalidateAll(actor.tenantId());
        }
        return ArtistView.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ArtistView getById(ActorContext actor, String artistId) {
        Artist artist = loadOrThrow(artistId, actor.tenantId());
        // DRAFT / ARCHIVED visibility is admin-only — non-admin sees a clean 404
        // (do not leak existence, do not leak status tier).
        if (artist.getStatus() != ArtistStatus.PUBLISHED && !actor.isAdmin()) {
            throw new ArtistNotFoundException(artistId);
        }
        return ArtistView.from(artist);
    }

    private Artist loadOrThrow(String rawId, String tenantId) {
        ArtistId id;
        try {
            id = ArtistId.of(rawId);
        } catch (IllegalArgumentException e) {
            throw new ArtistNotFoundException(rawId);
        }
        Optional<Artist> found = artistRepository.findById(id, tenantId);
        return found.orElseThrow(() -> new ArtistNotFoundException(rawId));
    }

    private static void requireAdmin(ActorContext actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new AdminRoleRequiredException();
        }
    }
}

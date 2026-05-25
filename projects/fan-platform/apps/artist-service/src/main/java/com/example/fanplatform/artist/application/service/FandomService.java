package com.example.fanplatform.artist.application.service;

import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.exception.ArtistNotFoundException;
import com.example.fanplatform.artist.application.exception.ArtistNotPublishedException;
import com.example.fanplatform.artist.application.exception.FandomAlreadyExistsException;
import com.example.fanplatform.artist.application.exception.FandomNotFoundException;
import com.example.fanplatform.artist.application.port.in.CreateFandomUseCase;
import com.example.fanplatform.artist.application.port.in.FandomView;
import com.example.fanplatform.artist.application.port.in.GetFandomUseCase;
import com.example.fanplatform.artist.application.port.in.UpdateFandomUseCase;
import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import com.example.fanplatform.artist.application.port.out.FandomRepository;
import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.fandom.Fandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fandom application service. Per task spec § Edge Cases:
 * <ul>
 *   <li>artist:fandom = 1:1 (second create -> 422 FANDOM_ALREADY_EXISTS)</li>
 *   <li>fandom can only be created when the artist is PUBLISHED</li>
 *   <li>update against a non-existing fandom -> 404 FANDOM_NOT_FOUND</li>
 * </ul>
 *
 * <p>Create and update are split (POST + PATCH respectively) so the
 * {@code FANDOM_ALREADY_EXISTS} path is reachable.
 */
@Service
@RequiredArgsConstructor
public class FandomService implements CreateFandomUseCase, UpdateFandomUseCase, GetFandomUseCase {

    private final FandomRepository fandomRepository;
    private final ArtistRepository artistRepository;

    @Override
    @Transactional
    public FandomView create(CreateFandomCommand cmd) {
        ActorGuard.requireAdmin(cmd.actor());
        String tenantId = cmd.actor().tenantId();
        ArtistId aid = ActorGuard.parseArtistId(cmd.artistId());

        Artist artist = artistRepository.findById(aid, tenantId)
                .orElseThrow(() -> new ArtistNotFoundException(cmd.artistId()));
        if (!artist.isPublished()) {
            throw new ArtistNotPublishedException(cmd.artistId());
        }
        if (fandomRepository.findByArtistId(aid, tenantId).isPresent()) {
            throw new FandomAlreadyExistsException(cmd.artistId());
        }
        Fandom fandom = Fandom.create(aid, tenantId, cmd.fandomName(), cmd.colorHex(),
                cmd.foundedAt(), cmd.slogan());
        Fandom saved = fandomRepository.insert(fandom);
        return FandomView.from(saved);
    }

    @Override
    @Transactional
    public FandomView update(UpdateFandomCommand cmd) {
        ActorGuard.requireAdmin(cmd.actor());
        String tenantId = cmd.actor().tenantId();
        ArtistId aid = ActorGuard.parseArtistId(cmd.artistId());

        Artist artist = artistRepository.findById(aid, tenantId)
                .orElseThrow(() -> new ArtistNotFoundException(cmd.artistId()));
        if (!artist.isPublished()) {
            throw new ArtistNotPublishedException(cmd.artistId());
        }
        Fandom fandom = fandomRepository.findByArtistId(aid, tenantId)
                .orElseThrow(() -> new FandomNotFoundException(cmd.artistId()));
        fandom.update(cmd.fandomName(), cmd.colorHex(), cmd.foundedAt(), cmd.slogan());
        Fandom saved = fandomRepository.update(fandom);
        return FandomView.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FandomView getByArtistId(ActorContext actor, String artistId) {
        String tenantId = actor.tenantId();
        ArtistId aid = ActorGuard.parseArtistId(artistId);
        Fandom fandom = fandomRepository.findByArtistId(aid, tenantId)
                .orElseThrow(() -> new FandomNotFoundException(artistId));
        return FandomView.from(fandom);
    }

}

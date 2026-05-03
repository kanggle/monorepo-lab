package com.example.fanplatform.artist.application.service;

import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.exception.AdminRoleRequiredException;
import com.example.fanplatform.artist.application.exception.ArtistNotFoundException;
import com.example.fanplatform.artist.application.exception.ArtistNotPublishedException;
import com.example.fanplatform.artist.application.exception.FandomNotFoundException;
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

import java.util.Optional;

/**
 * PUT-style upsert for the fandom aggregate. Per task spec § Edge Cases:
 * <ul>
 *   <li>artist:fandom = 1:1 (rejected only on create — update is fine)</li>
 *   <li>fandom can only be created/updated when the artist is PUBLISHED</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FandomService implements UpdateFandomUseCase, GetFandomUseCase {

    private final FandomRepository fandomRepository;
    private final ArtistRepository artistRepository;

    @Override
    @Transactional
    public FandomView upsert(UpdateFandomCommand cmd) {
        requireAdmin(cmd.actor());
        String tenantId = cmd.actor().tenantId();
        ArtistId aid = parseArtistId(cmd.artistId());

        Artist artist = artistRepository.findById(aid, tenantId)
                .orElseThrow(() -> new ArtistNotFoundException(cmd.artistId()));
        if (!artist.isPublished()) {
            throw new ArtistNotPublishedException(cmd.artistId());
        }

        Optional<Fandom> existing = fandomRepository.findByArtistId(aid, tenantId);
        Fandom saved;
        if (existing.isPresent()) {
            Fandom fandom = existing.get();
            fandom.update(cmd.fandomName(), cmd.colorHex(), cmd.foundedAt(), cmd.slogan());
            saved = fandomRepository.update(fandom);
        } else {
            Fandom fandom = Fandom.create(aid, tenantId, cmd.fandomName(), cmd.colorHex(),
                    cmd.foundedAt(), cmd.slogan());
            saved = fandomRepository.insert(fandom);
        }
        return FandomView.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FandomView getByArtistId(ActorContext actor, String artistId) {
        String tenantId = actor.tenantId();
        ArtistId aid = parseArtistId(artistId);
        Fandom fandom = fandomRepository.findByArtistId(aid, tenantId)
                .orElseThrow(() -> new FandomNotFoundException(artistId));
        return FandomView.from(fandom);
    }

    private static ArtistId parseArtistId(String rawId) {
        try {
            return ArtistId.of(rawId);
        } catch (IllegalArgumentException e) {
            throw new ArtistNotFoundException(rawId);
        }
    }

    private static void requireAdmin(ActorContext actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new AdminRoleRequiredException();
        }
    }
}

package com.example.fanplatform.artist.application.port.out;

import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.fandom.Fandom;

import java.util.Optional;

public interface FandomRepository {

    Fandom insert(Fandom fandom);

    Fandom update(Fandom fandom);

    Optional<Fandom> findByArtistId(ArtistId artistId, String tenantId);

    boolean existsByArtistId(ArtistId artistId, String tenantId);
}

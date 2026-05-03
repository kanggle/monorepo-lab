package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.domain.artist.ArtistType;

import java.time.LocalDate;

/** Inbound port: register a new artist (admin only). Result starts in DRAFT. */
public interface RegisterArtistUseCase {

    ArtistView register(RegisterArtistCommand command);

    record RegisterArtistCommand(
            ActorContext actor,
            ArtistType artistType,
            String stageName,
            String realName,
            LocalDate debutDate,
            String agency,
            String bio,
            String profileImageRef
    ) {}
}

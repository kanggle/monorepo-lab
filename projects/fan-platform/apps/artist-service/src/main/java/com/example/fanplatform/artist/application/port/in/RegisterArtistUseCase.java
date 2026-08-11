package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.domain.artist.ArtistType;

import java.time.LocalDate;

/** Inbound port: register a new artist (admin only). Result starts in DRAFT. */
public interface RegisterArtistUseCase {

    ArtistView register(RegisterArtistCommand command);

    record RegisterArtistCommand(
            ActorContext actor,
            // IAM subject that will author as this artist. Required — see
            // artist-api.md § accountId. NOT the registering admin's own
            // account: the admin registers on the artist's behalf.
            String accountId,
            ArtistType artistType,
            String stageName,
            String realName,
            LocalDate debutDate,
            String agency,
            String bio,
            String profileImageRef
    ) {}
}

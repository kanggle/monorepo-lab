package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;

import java.time.LocalDate;

/**
 * PUT-style upsert: creates the fandom on first call (artist must be PUBLISHED)
 * or updates fields on subsequent calls. Admin only.
 */
public interface UpdateFandomUseCase {

    FandomView upsert(UpdateFandomCommand command);

    record UpdateFandomCommand(
            ActorContext actor,
            String artistId,
            String fandomName,
            String colorHex,
            LocalDate foundedAt,
            String slogan
    ) {}
}

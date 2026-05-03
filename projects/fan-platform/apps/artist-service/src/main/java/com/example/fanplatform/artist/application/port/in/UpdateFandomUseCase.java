package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;

import java.time.LocalDate;

/**
 * Update-only fandom port. Returns 404 {@code FANDOM_NOT_FOUND} when no fandom
 * exists for the target artist — the create path lives on
 * {@link CreateFandomUseCase}. Admin only.
 */
public interface UpdateFandomUseCase {

    FandomView update(UpdateFandomCommand command);

    record UpdateFandomCommand(
            ActorContext actor,
            String artistId,
            String fandomName,
            String colorHex,
            LocalDate foundedAt,
            String slogan
    ) {}
}

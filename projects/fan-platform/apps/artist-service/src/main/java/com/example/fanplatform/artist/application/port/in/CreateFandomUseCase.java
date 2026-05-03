package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;

import java.time.LocalDate;

/**
 * Create-only fandom port. Per task spec § Edge Cases:
 * <ul>
 *   <li>artist:fandom = 1:1 — second create for the same artist returns 422
 *       {@code FANDOM_ALREADY_EXISTS}</li>
 *   <li>fandom can only be created when the artist is PUBLISHED — otherwise
 *       422 {@code ARTIST_NOT_PUBLISHED}</li>
 * </ul>
 *
 * <p>Subsequent edits go through {@link UpdateFandomUseCase}.
 */
public interface CreateFandomUseCase {

    FandomView create(CreateFandomCommand command);

    record CreateFandomCommand(
            ActorContext actor,
            String artistId,
            String fandomName,
            String colorHex,
            LocalDate foundedAt,
            String slogan
    ) {}
}

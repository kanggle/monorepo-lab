package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;

/** {DRAFT, PUBLISHED} → ARCHIVED transition. Admin only. */
public interface ArchiveArtistUseCase {

    ArtistView archive(ActorContext actor, String artistId, String reason);
}

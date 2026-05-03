package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;

/** DRAFT → PUBLISHED transition. Admin only. */
public interface PublishArtistUseCase {

    ArtistView publish(ActorContext actor, String artistId);
}

package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;

public interface GetFandomUseCase {

    FandomView getByArtistId(ActorContext actor, String artistId);
}

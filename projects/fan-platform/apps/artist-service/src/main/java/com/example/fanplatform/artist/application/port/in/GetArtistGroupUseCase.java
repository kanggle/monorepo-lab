package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;

public interface GetArtistGroupUseCase {

    ArtistGroupView getById(ActorContext actor, String groupId);
}

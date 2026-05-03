package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;

import java.time.LocalDate;

public interface CreateArtistGroupUseCase {

    ArtistGroupView create(CreateArtistGroupCommand command);

    record CreateArtistGroupCommand(
            ActorContext actor,
            String name,
            LocalDate debutDate,
            String agency,
            String profileImageRef
    ) {}
}

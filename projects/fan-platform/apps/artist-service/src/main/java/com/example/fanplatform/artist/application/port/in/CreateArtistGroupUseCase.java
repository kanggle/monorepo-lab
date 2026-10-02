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
            String profileImageRef,
            // Optional agency entity (TASK-MONO-748); null = unaffiliated.
            String agencyId
    ) {
        /** Pre-TASK-MONO-748 arity — no agency affiliation. */
        public CreateArtistGroupCommand(ActorContext actor, String name, LocalDate debutDate,
                                        String agency, String profileImageRef) {
            this(actor, name, debutDate, agency, profileImageRef, null);
        }
    }
}

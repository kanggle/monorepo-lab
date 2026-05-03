package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;

import java.time.LocalDate;
import java.util.List;

/** Update artist profile fields. Admin only. */
public interface UpdateArtistUseCase {

    ArtistView update(UpdateArtistCommand command);

    record UpdateArtistCommand(
            ActorContext actor,
            String artistId,
            String stageName,        // null → no change
            String realName,         // null → no change
            LocalDate debutDate,     // null → no change
            String agency,           // null → no change
            String bio,              // null → no change
            String profileImageRef   // null → no change
    ) {
        /** Names of fields that the caller intended to change (non-null arguments). */
        public List<String> changedFields() {
            List<String> out = new java.util.ArrayList<>();
            if (stageName != null) out.add("stageName");
            if (realName != null) out.add("realName");
            if (debutDate != null) out.add("debutDate");
            if (agency != null) out.add("agency");
            if (bio != null) out.add("bio");
            if (profileImageRef != null) out.add("profileImageRef");
            return out;
        }
    }
}

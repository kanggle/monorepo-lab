package com.example.fanplatform.artist.domain.fandom;

import com.example.fanplatform.artist.domain.artist.ArtistId;

import java.util.Objects;

/**
 * Fandom identity. Per task spec § Edge Cases, fandom is 1:1 with artist —
 * the artist_id IS the primary key, so this id wraps an {@link ArtistId}.
 */
public record FandomId(String value) {

    public FandomId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("FandomId must not be blank");
        }
    }

    public static FandomId of(String value) {
        return new FandomId(value);
    }

    public static FandomId fromArtistId(ArtistId artistId) {
        return new FandomId(artistId.value());
    }
}

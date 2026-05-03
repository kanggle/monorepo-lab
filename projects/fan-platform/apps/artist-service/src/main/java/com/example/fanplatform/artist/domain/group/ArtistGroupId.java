package com.example.fanplatform.artist.domain.group;

import java.util.Objects;

/** Identity for the {@code ArtistGroup} aggregate. UUID stored as VARCHAR(36). */
public record ArtistGroupId(String value) {

    public ArtistGroupId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ArtistGroupId must not be blank");
        }
    }

    public static ArtistGroupId of(String value) {
        return new ArtistGroupId(value);
    }
}

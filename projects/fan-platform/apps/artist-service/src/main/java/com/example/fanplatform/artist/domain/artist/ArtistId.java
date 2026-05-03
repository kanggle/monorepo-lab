package com.example.fanplatform.artist.domain.artist;

import java.util.Objects;

/**
 * Strongly-typed wrapper around the artist UUID. Stored as VARCHAR(36) — the
 * lib's {@code UuidV7} (or test fixtures) supply the value. Aggregate identity.
 */
public record ArtistId(String value) {

    public ArtistId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ArtistId must not be blank");
        }
    }

    public static ArtistId of(String value) {
        return new ArtistId(value);
    }
}

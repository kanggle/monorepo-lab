package com.example.fanplatform.artist.application.exception;

/**
 * Edge case: ARCHIVED artists cannot be added as new group members
 * (the artist's career has ended). Maps to 422 {@code ARTIST_ARCHIVED}.
 */
public class ArtistArchivedException extends RuntimeException {

    public ArtistArchivedException(String artistId) {
        super("Artist is archived and cannot be added as a group member: " + artistId);
    }
}

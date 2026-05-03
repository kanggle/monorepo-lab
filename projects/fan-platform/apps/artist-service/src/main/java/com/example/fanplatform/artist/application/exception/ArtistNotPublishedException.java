package com.example.fanplatform.artist.application.exception;

/**
 * Thrown when a fandom is created against a DRAFT (not yet PUBLISHED) artist.
 * Per task spec § Edge Cases: fandom is only allowed once the artist is
 * published.
 */
public class ArtistNotPublishedException extends RuntimeException {

    public ArtistNotPublishedException(String artistId) {
        super("Artist must be PUBLISHED before fandom operations: " + artistId);
    }
}

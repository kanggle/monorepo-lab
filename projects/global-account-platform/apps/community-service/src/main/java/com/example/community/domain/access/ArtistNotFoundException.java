package com.example.community.domain.access;

public class ArtistNotFoundException extends RuntimeException {
    public ArtistNotFoundException(String artistAccountId) {
        super("ARTIST_NOT_FOUND: " + artistAccountId);
    }
}

package com.example.fanplatform.artist.application.exception;

/**
 * Thrown when an artist is not found OR is not visible to the caller (DRAFT
 * / ARCHIVED + non-admin) OR is in a different tenant. The HTTP layer maps it
 * to 404 — never 403 — so we do not leak existence across tenants or status
 * tiers.
 */
public class ArtistNotFoundException extends RuntimeException {

    public ArtistNotFoundException(String artistId) {
        super("Artist not found: " + artistId);
    }
}

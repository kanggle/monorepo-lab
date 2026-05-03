package com.example.fanplatform.artist.application.exception;

/** Edge case: artist:fandom = 1:1 — second fandom for the same artist is rejected. */
public class FandomAlreadyExistsException extends RuntimeException {

    public FandomAlreadyExistsException(String artistId) {
        super("Fandom already exists for artist: " + artistId);
    }
}

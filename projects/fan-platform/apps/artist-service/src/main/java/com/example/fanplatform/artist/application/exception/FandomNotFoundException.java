package com.example.fanplatform.artist.application.exception;

public class FandomNotFoundException extends RuntimeException {

    public FandomNotFoundException(String artistId) {
        super("Fandom not found for artist: " + artistId);
    }
}

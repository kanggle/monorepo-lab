package com.example.fanplatform.artist.application.exception;

public class ArtistGroupNotFoundException extends RuntimeException {

    public ArtistGroupNotFoundException(String groupId) {
        super("Artist group not found: " + groupId);
    }
}

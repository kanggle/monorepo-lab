package com.example.fanplatform.artist.application.exception;

/** Edge case: same (group, artist) active membership added twice. */
public class AlreadyMemberException extends RuntimeException {

    public AlreadyMemberException(String groupId, String artistId) {
        super("Artist " + artistId + " is already an active member of group " + groupId);
    }
}

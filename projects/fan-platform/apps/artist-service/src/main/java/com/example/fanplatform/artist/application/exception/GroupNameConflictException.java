package com.example.fanplatform.artist.application.exception;

public class GroupNameConflictException extends RuntimeException {

    public GroupNameConflictException(String groupName) {
        super("Group name already exists in tenant: " + groupName);
    }
}

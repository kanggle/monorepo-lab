package com.example.fanplatform.community.application.exception;

public class AlreadyFollowingException extends RuntimeException {
    public AlreadyFollowingException() {
        super("ALREADY_FOLLOWING");
    }
}

package com.example.community.application.exception;

public class AlreadyFollowingException extends RuntimeException {
    public AlreadyFollowingException() {
        super("ALREADY_FOLLOWING");
    }
}

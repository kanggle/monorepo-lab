package com.example.auth.application.exception;

public class SessionRevokedException extends RuntimeException {

    public SessionRevokedException() {
        super("Session has been revoked");
    }
}

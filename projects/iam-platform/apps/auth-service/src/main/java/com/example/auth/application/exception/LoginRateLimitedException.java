package com.example.auth.application.exception;

public class LoginRateLimitedException extends RuntimeException {

    public LoginRateLimitedException() {
        super("Too many login attempts. Try again later.");
    }
}

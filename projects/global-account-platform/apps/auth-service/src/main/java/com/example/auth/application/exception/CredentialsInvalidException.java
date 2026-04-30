package com.example.auth.application.exception;

public class CredentialsInvalidException extends RuntimeException {

    public CredentialsInvalidException() {
        super("Invalid credentials");
    }
}

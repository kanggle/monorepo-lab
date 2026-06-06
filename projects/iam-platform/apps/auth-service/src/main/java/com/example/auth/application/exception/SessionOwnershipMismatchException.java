package com.example.auth.application.exception;

public class SessionOwnershipMismatchException extends RuntimeException {
    public SessionOwnershipMismatchException() {
        super("Session does not belong to caller");
    }
}

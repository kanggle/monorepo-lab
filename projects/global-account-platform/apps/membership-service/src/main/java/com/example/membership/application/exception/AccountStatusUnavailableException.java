package com.example.membership.application.exception;

public class AccountStatusUnavailableException extends RuntimeException {

    public AccountStatusUnavailableException(String message) {
        super(message);
    }

    public AccountStatusUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

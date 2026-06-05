package com.example.account.application.exception;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String identifier) {
        super("Account not found: " + identifier);
    }
}

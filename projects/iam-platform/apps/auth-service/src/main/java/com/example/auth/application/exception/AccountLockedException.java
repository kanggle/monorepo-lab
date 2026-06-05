package com.example.auth.application.exception;

public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("Account is locked");
    }
}

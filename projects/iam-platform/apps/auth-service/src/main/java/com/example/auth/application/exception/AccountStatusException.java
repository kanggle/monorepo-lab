package com.example.auth.application.exception;

/**
 * Thrown when account status prevents login (DORMANT, DELETED).
 */
public class AccountStatusException extends RuntimeException {

    private final String status;
    private final String errorCode;

    public AccountStatusException(String status, String errorCode) {
        super("Account status is " + status);
        this.status = status;
        this.errorCode = errorCode;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

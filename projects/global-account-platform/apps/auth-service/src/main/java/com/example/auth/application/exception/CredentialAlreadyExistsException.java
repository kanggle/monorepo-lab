package com.example.auth.application.exception;

/**
 * Thrown when {@code POST /internal/auth/credentials} is called for an accountId
 * that already has a credential row. Mapped to HTTP 409.
 */
public class CredentialAlreadyExistsException extends RuntimeException {

    private final String accountId;

    public CredentialAlreadyExistsException(String accountId) {
        super("Credential already exists for account");
        this.accountId = accountId;
    }

    public String getAccountId() {
        return accountId;
    }
}

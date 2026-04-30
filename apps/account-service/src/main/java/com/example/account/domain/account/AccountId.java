package com.example.account.domain.account;

import java.util.UUID;

/**
 * Value object for account identifier.
 */
public record AccountId(String value) {

    public AccountId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AccountId must not be blank");
        }
    }

    public static AccountId generate() {
        return new AccountId(UUID.randomUUID().toString());
    }

    public static AccountId of(String value) {
        return new AccountId(value);
    }
}

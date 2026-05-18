package com.example.finance.account.domain.account;

import java.util.Objects;

/**
 * Typed account identifier (opaque UUID v7 string). Keeps account id passing
 * type-safe through the domain without leaking the raw String everywhere.
 */
public record AccountId(String value) {

    public AccountId {
        Objects.requireNonNull(value, "accountId");
        if (value.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
    }

    public static AccountId of(String value) {
        return new AccountId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

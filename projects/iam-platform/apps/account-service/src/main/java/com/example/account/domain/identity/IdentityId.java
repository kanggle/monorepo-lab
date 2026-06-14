package com.example.account.domain.identity;

import java.util.UUID;

/**
 * Value object for the central identity identifier (ADR-MONO-034 U1-A).
 *
 * <p>An {@code IdentityId} is a fresh UUID, deliberately distinct from the
 * consumer {@code AccountId} — the person-id and the consumer-account-id spaces
 * are kept separate (U1-A rejects reusing {@code accounts.id} as the person id).
 */
public record IdentityId(String value) {

    public IdentityId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("IdentityId must not be blank");
        }
    }

    public static IdentityId generate() {
        return new IdentityId(UUID.randomUUID().toString());
    }

    public static IdentityId of(String value) {
        return new IdentityId(value);
    }
}

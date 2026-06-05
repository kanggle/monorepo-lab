package com.example.auth.domain.credentials;

import java.util.Objects;

/**
 * Value object wrapping a credential hash and its algorithm.
 */
public record CredentialHash(String hash, String algorithm) {

    public CredentialHash {
        Objects.requireNonNull(hash, "hash must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");
    }

    public static CredentialHash argon2id(String hash) {
        return new CredentialHash(hash, "argon2id");
    }
}

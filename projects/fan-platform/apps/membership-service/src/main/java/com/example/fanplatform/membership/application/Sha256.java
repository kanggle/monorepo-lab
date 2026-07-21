package com.example.fanplatform.membership.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 → lowercase hex. Shared by the idempotency-fingerprint builders in
 * {@link SubscribeUseCase} and {@link RenewMembershipUseCase}: extracting only
 * the digest tail keeps each use case's payload composition (its {@code raw}
 * string) local while removing the byte-identical hashing step. The hashed bytes
 * are unchanged, so every fingerprint value is preserved.
 */
final class Sha256 {

    private Sha256() {
    }

    static String hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

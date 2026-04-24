package com.example.auth.infrastructure.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class TokenKeyHasher {

    private static final HexFormat HEX = HexFormat.of();

    private TokenKeyHasher() {}

    public static String sha256Hex(String input) {
        Objects.requireNonNull(input, "input must not be null");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

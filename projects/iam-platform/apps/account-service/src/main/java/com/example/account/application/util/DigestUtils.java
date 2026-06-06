package com.example.account.application.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Cryptographic digest utilities shared across application and infrastructure layers.
 *
 * <p>Centralizes SHA-256 hashing to avoid duplicated implementations in
 * {@code GdprDeleteUseCase}, {@code PiiAnonymizer}, and {@code AccountEventPublisher}.
 */
public final class DigestUtils {

    private DigestUtils() {
        // utility class
    }

    /**
     * Compute SHA-256 of the given input and return the full 64-character lowercase hex string.
     *
     * @param input the string to hash (UTF-8 encoded)
     * @return 64-char lowercase hex string
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Compute SHA-256 of the given input and return the first {@code length} characters of the hex string.
     *
     * @param input  the string to hash (UTF-8 encoded)
     * @param length number of leading hex characters to return (must be 1–64)
     * @return first {@code length} chars of the 64-char SHA-256 hex string
     */
    public static String sha256Short(String input, int length) {
        return sha256Hex(input).substring(0, length);
    }
}

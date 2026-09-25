package com.example.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * TASK-BE-599 — the two PII-safe hashes every login-event producer derives.
 *
 * <p>Hoisted out of {@link LoginUseCase} so the form-login path
 * ({@link LoginEventRecorder}) emits byte-identical {@code emailHash} /
 * {@code deviceFingerprintHash} values instead of a second copy of the algorithm
 * drifting away from the first. {@link LoginUseCase#hashEmail} and
 * {@link LoginUseCase#fingerprintHash} now delegate here.
 */
public final class LoginHashes {

    private LoginHashes() {
    }

    /**
     * {@code auth.login.*} payload {@code emailHash}: SHA-256 of the lower-cased email,
     * hex, first 10 chars ({@code auth-events.md} "SHA256[:10]").
     */
    public static String emailHash(String email) {
        return HexFormat.of().formatHex(sha256(email.toLowerCase())).substring(0, 10);
    }

    /**
     * {@code auth.session.created} payload {@code deviceFingerprintHash}: SHA-256 of the raw
     * fingerprint, hex. {@code null} for a missing/blank fingerprint.
     */
    public static String fingerprintHash(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        return HexFormat.of().formatHex(sha256(fingerprint));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

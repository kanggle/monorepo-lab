package com.example.admin.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * TASK-MONO-772 S2 (rider R4) — the invitation token: 32 {@link SecureRandom} bytes → base64url (no padding), and
 * its SHA-256 hex, which is the ONLY form that is stored. The same computation as the seller-member invitation
 * ({@code SellerMemberService.newToken} / {@code sha256Hex}, product-service) so the two invitation kinds cannot
 * drift in strength.
 *
 * <p>S3's acceptance hashes the presented token with {@link #sha256Hex} and looks the row up by the hash — the raw
 * value is never compared, never stored, never logged.
 */
public final class OperatorInvitationTokens {

    static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private OperatorInvitationTokens() {
    }

    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

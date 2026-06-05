package com.example.admin.infrastructure.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * RFC 6238 TOTP generator/verifier (HMAC-SHA1, 6 digits, 30s step, ±1 window).
 *
 * <p>Used by {@code AdminAuthController} for 2FA enroll/verify. The shared
 * secret is stored encrypted (see {@link TotpSecretCipher}); this class
 * operates only on plaintext bytes in memory.
 *
 * <p>RFC 4648 Base32 (upper-case, no padding by default for otpauth URIs).
 */
@Component
public class TotpGenerator {

    private static final String HMAC_ALGO = "HmacSHA1";
    private static final int DIGITS = 6;
    private static final long TIME_STEP_SECONDS = 30L;
    /** Secret length per RFC 6238 recommendation (160 bits for HMAC-SHA1). */
    private static final int SECRET_BYTES = 20;
    private static final int WINDOW = 1;
    private static final int[] POW10 = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000};

    private static final char[] BASE32_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private final SecureRandom secureRandom;
    private final Clock clock;

    public TotpGenerator() {
        this(new SecureRandom(), Clock.systemUTC());
    }

    public TotpGenerator(SecureRandom secureRandom, Clock clock) {
        this.secureRandom = Objects.requireNonNull(secureRandom);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Produces a fresh 160-bit secret suitable for otpauth enrollment. */
    public byte[] newSecret() {
        byte[] s = new byte[SECRET_BYTES];
        secureRandom.nextBytes(s);
        return s;
    }

    /** Current 6-digit TOTP code using the configured clock. */
    public String code(byte[] secret) {
        return code(secret, currentCounter());
    }

    /** Verifies a 6-digit code against the current counter ±{@value #WINDOW}. */
    public boolean verify(byte[] secret, String code) {
        if (secret == null || code == null) return false;
        String normalized = code.trim();
        if (normalized.length() != DIGITS) return false;
        long now = currentCounter();
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            String candidate = code(secret, now + offset);
            if (constantTimeEquals(candidate, normalized)) return true;
        }
        return false;
    }

    /**
     * Builds the {@code otpauth://totp/...} URI per Google Authenticator spec.
     * Both {@code issuer} and {@code accountLabel} are URL-encoded.
     */
    public String otpauthUri(byte[] secret, String issuer, String accountLabel) {
        Objects.requireNonNull(secret);
        Objects.requireNonNull(issuer);
        Objects.requireNonNull(accountLabel);
        String label = urlEncode(issuer) + ":" + urlEncode(accountLabel);
        return "otpauth://totp/" + label
                + "?secret=" + base32Encode(secret)
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS
                + "&period=" + TIME_STEP_SECONDS;
    }

    /** Public for deterministic tests (RFC 6238 time vectors). */
    public String code(byte[] secret, long counter) {
        byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();
        byte[] hash;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            hash = mac.doFinal(msg);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA1 unavailable", e);
        }
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % POW10[DIGITS];
        String s = Integer.toString(otp);
        StringBuilder out = new StringBuilder(DIGITS);
        for (int i = s.length(); i < DIGITS; i++) out.append('0');
        out.append(s);
        return out.toString();
    }

    private long currentCounter() {
        return Instant.now(clock).getEpochSecond() / TIME_STEP_SECONDS;
    }

    // --- RFC 4648 Base32 (no padding — accepted by Google Authenticator) ----
    public static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                out.append(BASE32_ALPHABET[index]);
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            out.append(BASE32_ALPHABET[index]);
        }
        return out.toString();
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}

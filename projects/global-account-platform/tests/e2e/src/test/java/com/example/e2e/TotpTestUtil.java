package com.example.e2e;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Independent RFC 6238 TOTP implementation used only by the E2E scenarios.
 * Must NOT depend on admin-service's internal {@code TotpGenerator} (041c
 * §6 — independent module requirement); we re-derive codes from the
 * {@code otpauthUri} returned by {@code /api/admin/auth/2fa/enroll}.
 *
 * <p>Algorithm: HMAC-SHA1, 6 digits, 30s step, RFC 4648 Base32 (no padding).
 * Matches admin-service's generator parameters.
 */
public final class TotpTestUtil {

    private static final int DIGITS = 6;
    private static final long STEP_SECONDS = 30L;
    private static final int[] POW10 = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000};
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpTestUtil() {}

    /** Extracts the Base32-encoded shared secret from an otpauth:// URI. */
    public static String extractSecret(String otpauthUri) {
        int q = otpauthUri.indexOf('?');
        if (q < 0) throw new IllegalArgumentException("otpauth uri missing query: " + otpauthUri);
        for (String kv : otpauthUri.substring(q + 1).split("&")) {
            int eq = kv.indexOf('=');
            if (eq <= 0) continue;
            String k = kv.substring(0, eq);
            String v = URLDecoder.decode(kv.substring(eq + 1), StandardCharsets.UTF_8);
            if ("secret".equals(k)) return v;
        }
        throw new IllegalArgumentException("otpauth uri missing 'secret': " + otpauthUri);
    }

    /** Current 6-digit TOTP code for the given Base32 secret. */
    public static String codeNow(String base32Secret) {
        return codeAt(base32Secret, Instant.now().getEpochSecond() / STEP_SECONDS);
    }

    /** Current ± offset window (e.g., -1 for previous step, +1 for next). */
    public static String codeAtOffset(String base32Secret, int stepOffset) {
        return codeAt(base32Secret, (Instant.now().getEpochSecond() / STEP_SECONDS) + stepOffset);
    }

    public static String codeAt(String base32Secret, long counter) {
        byte[] key = base32Decode(base32Secret);
        byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();
        byte[] hash;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            hash = mac.doFinal(msg);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
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

    /** RFC 4648 Base32 decode (case-insensitive, no padding required). */
    public static byte[] base32Decode(String s) {
        String clean = s.trim().toUpperCase().replace("=", "").replace(" ", "");
        int bits = 0, buffer = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(clean.length() * 5 / 8);
        for (int i = 0; i < clean.length(); i++) {
            int idx = BASE32.indexOf(clean.charAt(i));
            if (idx < 0) throw new IllegalArgumentException("Invalid Base32 char at " + i + ": " + clean.charAt(i));
            buffer = (buffer << 5) | idx;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}

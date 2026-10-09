package com.example.auth.infrastructure.totp;

import com.example.auth.application.port.TotpCodeCalculator;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * TASK-MONO-771 S2b — local RFC 6238 / RFC 4226 implementation of {@link TotpCodeCalculator}
 * (HMAC-SHA1 · 6 digits · 30 s), plus RFC 4648 Base32 for the secret an authenticator app is given.
 *
 * <p>🔵 <b>Temporary.</b> To be replaced by the {@code libs/java-security} TOTP calculator after TASK-MONO-771
 * S2a merges (S2a promotes admin-service's computation there). Written independently here so the two slices do
 * not collide — it is NOT a copy of admin-service internals; it implements the RFC directly and is pinned by the
 * RFC 6238 Appendix B SHA-1 vectors in its test. When the swap happens, that test is the equivalence check.
 */
@Component
public class Rfc6238TotpCodeCalculator implements TotpCodeCalculator {

    private static final int DIGITS = 6;
    private static final int MODULUS = 1_000_000;
    private static final int SECRET_BYTES = 20;
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private final SecureRandom random = new SecureRandom();

    @Override
    public byte[] newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return secret;
    }

    @Override
    public String codeAt(byte[] secret, long timeStep) {
        byte[] hash = hmacSha1(secret, ByteBuffer.allocate(Long.BYTES).putLong(timeStep).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        String code = Integer.toString(binary % MODULUS);
        return "0".repeat(DIGITS - code.length()) + code;
    }

    @Override
    public String toBase32(byte[] secret) {
        StringBuilder out = new StringBuilder((secret.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : secret) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32[(buffer >> (bits - 5)) & 0x1f]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32[(buffer << (5 - bits)) & 0x1f]);
        }
        return out.toString();
    }

    private static byte[] hmacSha1(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }
}

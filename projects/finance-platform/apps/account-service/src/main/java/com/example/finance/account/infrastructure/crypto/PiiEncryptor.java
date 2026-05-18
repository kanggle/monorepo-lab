package com.example.finance.account.infrastructure.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Column-level AES-256-GCM encryption for regulated PII / financial
 * identifiers (fintech F7, regulated trait). Used on the account
 * {@code owner_ref} (external owner id) — never stored, logged, evented, or
 * returned in plaintext.
 *
 * <p>Envelope layout: {@code [12-byte IV][ciphertext][16-byte GCM tag]}
 * (the GCM tag is appended by the cipher when encryption finalizes; we only
 * prepend the IV). The whole envelope is Base64 for a {@code VARCHAR} column.
 *
 * <p>Key: {@code financeplatform.account.crypto.pii-key} (env override
 * {@code FINANCE_ACCOUNT_PII_KEY}); per-row {@code encryption_key_id} = "v1".
 * <b>Boot self-test (Failure Mode #15)</b>: the constructor round-trips a
 * probe; a missing/wrong-length key fails the context fast.
 */
@Component
public class PiiEncryptor {

    public static final String ACTIVE_KEY_ID = "v1";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public PiiEncryptor(
            @Value("${financeplatform.account.crypto.pii-key}") String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalStateException(
                    "financeplatform.account.crypto.pii-key is required (F7)");
        }
        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            // Dev convenience: pad/truncate to 32 bytes (AES-256). Production
            // MUST supply a 32-byte FINANCE_ACCOUNT_PII_KEY.
            byte[] padded = new byte[32];
            for (int i = 0; i < 32; i++) {
                padded[i] = i < keyBytes.length ? keyBytes[i] : (byte) 0;
            }
            keyBytes = padded;
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
        try {
            byte[] probe = encrypt("self-test".getBytes(StandardCharsets.UTF_8));
            byte[] back = decrypt(probe);
            if (!"self-test".equals(new String(back, StandardCharsets.UTF_8))) {
                throw new IllegalStateException("crypto self-test mismatch");
            }
        } catch (Exception e) {
            throw new IllegalStateException("PiiEncryptor self-test failed", e);
        }
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] out = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(IV_LENGTH + out.length).put(iv).put(out).array();
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encrypt failed", e);
        }
    }

    public byte[] decrypt(byte[] envelope) {
        try {
            if (envelope == null || envelope.length <= IV_LENGTH) {
                throw new IllegalArgumentException("envelope too short");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ct = new byte[envelope.length - IV_LENGTH];
            System.arraycopy(envelope, 0, iv, 0, IV_LENGTH);
            System.arraycopy(envelope, IV_LENGTH, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decrypt failed", e);
        }
    }

    /** Base64 of {@code "v1:" + base64(envelope)} — the column wire form. */
    public String encryptToString(String plaintext) {
        if (plaintext == null) return null;
        String b64 = Base64.getEncoder().encodeToString(
                encrypt(plaintext.getBytes(StandardCharsets.UTF_8)));
        return ACTIVE_KEY_ID + ":" + b64;
    }

    public String decryptFromString(String stored) {
        if (stored == null) return null;
        int sep = stored.indexOf(':');
        String b64 = sep >= 0 ? stored.substring(sep + 1) : stored;
        return new String(decrypt(Base64.getDecoder().decode(b64)),
                StandardCharsets.UTF_8);
    }
}

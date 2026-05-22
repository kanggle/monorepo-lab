package com.example.scmplatform.procurement.infrastructure.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Column-level AES-GCM encryption for supplier credentials (S6).
 *
 * <p>Layout: {@code [12-byte IV][ciphertext][16-byte tag]}. Tag is appended
 * by AES-GCM mode automatically when encryption finalizes — we just write
 * the IV in front of the cipher output.
 *
 * <p>Key handling: the active 32-byte key is read from
 * {@code scmplatform.procurement.crypto.supplier-credentials-key}. v2 will
 * migrate to a vault with per-supplier key id rotation; v1 uses a single
 * key id {@code "v1"} on every row for forward compatibility.
 *
 * <p>Boot self-test (Failure Scenario H): the constructor performs a
 * round-trip encrypt/decrypt to fail-fast if the key is missing or the
 * wrong length.
 */
// `final` so the self-test calls to `encrypt(...)` + `decrypt(...)` in the
// constructor cannot be observed by an unfinished subclass — silences the
// {@code [this-escape]} warning from {@code javac -Xlint:all}.
// SupplierCredentialsEncryptor is a crypto component; subclassing is not part
// of its design contract.
@Component
public final class SupplierCredentialsEncryptor {

    public static final String ACTIVE_KEY_ID = "v1";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SupplierCredentialsEncryptor(
            @Value("${scmplatform.procurement.crypto.supplier-credentials-key}") String rawKey) {
        if (rawKey == null) {
            throw new IllegalStateException(
                    "scmplatform.procurement.crypto.supplier-credentials-key is required");
        }
        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            // Truncate / pad to 32 bytes for dev convenience. In production
            // the SUPPLIER_CREDENTIALS_KEY env var is required to be exactly
            // 32 bytes (AES-256).
            byte[] padded = new byte[32];
            for (int i = 0; i < 32; i++) {
                padded[i] = i < keyBytes.length ? keyBytes[i] : (byte) 0;
            }
            keyBytes = padded;
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
        // Boot self-test: round-trip a probe to fail-fast on misconfiguration.
        try {
            byte[] probe = encrypt("probe".getBytes(StandardCharsets.UTF_8));
            byte[] roundTripped = decrypt(probe);
            if (!"probe".equals(new String(roundTripped, StandardCharsets.UTF_8))) {
                throw new IllegalStateException("crypto self-test mismatch");
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "SupplierCredentialsEncryptor self-test failed", e);
        }
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherOut = cipher.doFinal(plaintext);
            ByteBuffer buf = ByteBuffer.allocate(IV_LENGTH + cipherOut.length);
            buf.put(iv);
            buf.put(cipherOut);
            return buf.array();
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
            byte[] cipherOut = new byte[envelope.length - IV_LENGTH];
            System.arraycopy(envelope, 0, iv, 0, IV_LENGTH);
            System.arraycopy(envelope, IV_LENGTH, cipherOut, 0, cipherOut.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(cipherOut);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decrypt failed", e);
        }
    }
}

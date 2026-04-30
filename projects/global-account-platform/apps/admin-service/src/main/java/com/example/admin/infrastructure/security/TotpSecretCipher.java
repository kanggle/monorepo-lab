package com.example.admin.infrastructure.security;

import com.example.admin.infrastructure.config.AdminTotpProperties;
import jakarta.annotation.PostConstruct;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AES-GCM (256-bit) cipher for admin operator TOTP shared secrets.
 *
 * <p>Layout of ciphertext blob stored in
 * {@code admin_operator_totp.secret_encrypted}:
 * <pre>
 *   [ 12-byte random IV ][ ciphertext ][ 16-byte auth tag ]
 * </pre>
 * <p>AAD: 8-byte big-endian representation of the admin_operators.id BIGINT
 * (row-swap defense — a ciphertext produced for operator A will not decrypt
 * for operator B even if the blob is copied across rows).
 *
 * <p>See specs/services/admin-service/security.md §TOTP Secret Encryption.
 */
public class TotpSecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32; // AES-256

    private final AdminTotpProperties properties;
    private final SecureRandom secureRandom;
    private final Map<String, SecretKey> keysById = new LinkedHashMap<>();
    private String activeKeyId;

    public TotpSecretCipher(AdminTotpProperties properties) {
        this(properties, new SecureRandom());
    }

    public TotpSecretCipher(AdminTotpProperties properties, SecureRandom secureRandom) {
        this.properties = Objects.requireNonNull(properties);
        this.secureRandom = Objects.requireNonNull(secureRandom);
    }

    @PostConstruct
    public void init() {
        this.activeKeyId = properties.getEncryptionKeyId();
        Map<String, String> encoded = properties.getEncryptionKeys();
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalStateException("admin.totp.encryption-keys must not be empty");
        }
        for (Map.Entry<String, String> e : encoded.entrySet()) {
            keysById.put(e.getKey(), decodeKey(e.getKey(), e.getValue()));
        }
        if (!keysById.containsKey(activeKeyId)) {
            throw new IllegalStateException(
                    "admin.totp.encryption-key-id=" + activeKeyId
                            + " is not present in admin.totp.encryption-keys");
        }
    }

    public String activeKeyId() {
        return activeKeyId;
    }

    /**
     * Encrypts {@code plaintext} with the active key using a fresh random IV.
     * @param plaintext  raw TOTP secret bytes (never persisted in plaintext)
     * @param operatorId admin_operators.id BIGINT — bound as AAD
     */
    public byte[] encrypt(byte[] plaintext, long operatorId) {
        SecretKey key = keysById.get(activeKeyId);
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        byte[] aad = aadFor(operatorId);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            byte[] cipherAndTag = cipher.doFinal(plaintext);
            byte[] out = new byte[iv.length + cipherAndTag.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherAndTag, 0, out, iv.length, cipherAndTag.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encrypt failed", e);
        }
    }

    /**
     * Decrypts a blob produced by {@link #encrypt(byte[], long)} using the key
     * identified by {@code keyId}. Throws {@link IllegalStateException} if the
     * key id is unknown, the blob is malformed, or the auth tag/AAD check
     * fails.
     */
    public byte[] decrypt(byte[] blob, long operatorId, String keyId) {
        if (blob == null || blob.length < IV_BYTES + (TAG_BITS / 8)) {
            throw new IllegalStateException("TOTP ciphertext blob is too short");
        }
        SecretKey key = keysById.get(keyId);
        if (key == null) {
            throw new IllegalStateException("Unknown TOTP encryption kid: " + keyId);
        }
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(blob, 0, iv, 0, IV_BYTES);
        byte[] cipherAndTag = new byte[blob.length - IV_BYTES];
        System.arraycopy(blob, IV_BYTES, cipherAndTag, 0, cipherAndTag.length);
        byte[] aad = aadFor(operatorId);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(cipherAndTag);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decrypt failed", e);
        }
    }

    private static byte[] aadFor(long operatorId) {
        return ByteBuffer.allocate(Long.BYTES).putLong(operatorId).array();
    }

    private static SecretKey decodeKey(String kid, String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("admin.totp.encryption-keys[" + kid + "] is blank");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "admin.totp.encryption-keys[" + kid + "] is not valid base64", ex);
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "admin.totp.encryption-keys[" + kid + "] must decode to " + KEY_BYTES
                            + " bytes (AES-256); got " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }
}

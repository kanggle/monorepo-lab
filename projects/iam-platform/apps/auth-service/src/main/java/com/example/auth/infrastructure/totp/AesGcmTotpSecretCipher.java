package com.example.auth.infrastructure.totp;

import com.example.auth.application.port.TotpSecretCipher;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * TASK-MONO-771 — {@link TotpSecretCipher} over AES-GCM-256: a random 12-byte IV per write, 128-bit tag, one
 * value {@code [IV][ciphertext][tag]}, AAD = the UTF-8 bytes of {@code account_id} (data-model.md §
 * account_totp). Keys come from {@code auth.totp.encryption-keys}; every key is validated at construction
 * (base64 of exactly 32 bytes) so a bad key fails the boot, never the first enrollment.
 *
 * <p>R4: no key, secret or plaintext ever reaches a log line or an exception message.
 */
public class AesGcmTotpSecretCipher implements TotpSecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final Map<String, SecretKeySpec> keys;
    private final String activeKeyId;
    private final SecureRandom random = new SecureRandom();

    public AesGcmTotpSecretCipher(String activeKeyId, Map<String, String> base64Keys) {
        if (activeKeyId == null || activeKeyId.isBlank()) {
            throw new IllegalStateException("auth.totp.encryption-key-id must be set");
        }
        if (base64Keys == null || base64Keys.isEmpty()) {
            throw new IllegalStateException("auth.totp.encryption-keys must hold at least one key");
        }
        Map<String, SecretKeySpec> parsed = new HashMap<>();
        base64Keys.forEach((id, encoded) -> parsed.put(id, parseKey(id, encoded)));
        if (!parsed.containsKey(activeKeyId)) {
            throw new IllegalStateException("auth.totp.encryption-keys has no key for the active id '"
                    + activeKeyId + "'");
        }
        this.keys = Map.copyOf(parsed);
        this.activeKeyId = activeKeyId;
    }

    private static SecretKeySpec parseKey(String id, String encoded) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded == null ? "" : encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("auth.totp.encryption-keys." + id + " is not base64");
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException("auth.totp.encryption-keys." + id + " must decode to 32 bytes");
        }
        return new SecretKeySpec(raw, "AES");
    }

    @Override
    public Sealed encrypt(byte[] secret, String accountId) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(accountId));
            byte[] sealed = cipher.doFinal(secret);
            return new Sealed(ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array(),
                    activeKeyId);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP secret encryption failed");
        }
    }

    @Override
    public byte[] decrypt(byte[] sealed, String keyId, String accountId) {
        SecretKeySpec key = keys.get(keyId);
        if (key == null) {
            throw new IllegalStateException("no TOTP encryption key for id '" + keyId + "'");
        }
        if (sealed == null || sealed.length <= IV_BYTES) {
            throw new IllegalStateException("TOTP secret ciphertext is malformed");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES));
            cipher.updateAAD(aad(accountId));
            return cipher.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            // Tampered row, a row moved onto another account (AAD), or the wrong key — never a wrong secret.
            throw new IllegalStateException("TOTP secret could not be decrypted");
        }
    }

    @Override
    public String activeKeyId() {
        return activeKeyId;
    }

    private static byte[] aad(String accountId) {
        return accountId.getBytes(StandardCharsets.UTF_8);
    }
}

package com.example.auth.application.port;

/**
 * TASK-MONO-771 — encryption at rest of the account TOTP secret (data-model.md § account_totp
 * {@code secret_encrypted}; regulated R2/R9). AES-GCM-256, a fresh random 12-byte IV per write, 128-bit tag,
 * one column {@code [IV][ciphertext][tag]}, <b>AAD = the UTF-8 bytes of {@code account_id}</b> (a row copied
 * onto another account does not decrypt). Same scheme as admin-service's break-glass secret, different key
 * ({@code auth.totp.*} — a per-service key).
 */
public interface TotpSecretCipher {

    /** Encrypts under the ACTIVE key. */
    Sealed encrypt(byte[] secret, String accountId);

    /**
     * Decrypts with the key named by {@code keyId}.
     *
     * @throws IllegalStateException when the key id is unknown or authentication fails (tampered row,
     *                               swapped row, wrong key) — never returns a wrong secret
     */
    byte[] decrypt(byte[] sealed, String keyId, String accountId);

    /** The key id new writes are sealed with — a row on another id is lazily re-encrypted. */
    String activeKeyId();

    /** A sealed secret and the id of the key that sealed it. */
    record Sealed(byte[] ciphertext, String keyId) {
    }
}

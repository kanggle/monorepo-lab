package com.example.auth.infrastructure.totp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * TASK-MONO-771 — {@code auth.totp.*}: the account-plane TOTP settings. A per-service key, distinct from
 * admin-service's {@code admin.totp.*} (data-model.md § account_totp {@code secret_encrypted}).
 *
 * @param issuer          the issuer label shown in the authenticator app ({@code otpauth://totp/<issuer>:…})
 * @param encryptionKeyId the key id NEW writes are sealed with (rotation: add a key, flip this, re-encrypt
 *                        lazily on the next successful use, retire the old key — admin's procedure)
 * @param encryptionKeys  key id → base64 of a 32-byte AES key
 */
@ConfigurationProperties(prefix = "auth.totp")
public record TotpProperties(String issuer, String encryptionKeyId, Map<String, String> encryptionKeys) {
}

package com.example.admin.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * admin-service TOTP secret encryption configuration.
 *
 * <p>Canonical source for the AES-GCM key registry used by
 * {@link com.example.admin.infrastructure.security.TotpSecretCipher}.
 * Multiple kids may coexist during rotation (security.md §Rotation Procedure).
 * {@code encryptionKeyId} selects the active kid for new writes; reads pick the
 * matching kid from the row's {@code secret_key_id} column.
 *
 * <ul>
 *   <li>{@code encryptionKeyId} — active kid (must be present in {@link #getEncryptionKeys()}).</li>
 *   <li>{@code encryptionKeys} — kid → base64-encoded 32-byte AES key.</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "admin.totp")
public class AdminTotpProperties {

    @NotBlank
    private String encryptionKeyId;

    @NotEmpty
    @NotNull
    private Map<String, String> encryptionKeys;

    public String getEncryptionKeyId() {
        return encryptionKeyId;
    }

    public void setEncryptionKeyId(String encryptionKeyId) {
        this.encryptionKeyId = encryptionKeyId;
    }

    public Map<String, String> getEncryptionKeys() {
        return encryptionKeys;
    }

    public void setEncryptionKeys(Map<String, String> encryptionKeys) {
        this.encryptionKeys = encryptionKeys;
    }
}

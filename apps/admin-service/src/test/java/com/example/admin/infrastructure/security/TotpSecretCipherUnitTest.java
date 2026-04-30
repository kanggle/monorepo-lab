package com.example.admin.infrastructure.security;

import com.example.admin.infrastructure.config.AdminTotpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TotpSecretCipher 단위 테스트")
class TotpSecretCipherUnitTest {

    private static final long OPERATOR_ID = 42L;
    private static final byte[] PLAINTEXT = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    private TotpSecretCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = buildCipher("key-1", randomAes256KeyBase64());
    }

    @Test
    @DisplayName("encrypt → decrypt 라운드트립 → 원본 plaintext 복원")
    void encryptDecrypt_roundtrip_returnsSamePlaintext() {
        byte[] encrypted = cipher.encrypt(PLAINTEXT, OPERATOR_ID);
        byte[] decrypted = cipher.decrypt(encrypted, OPERATOR_ID, "key-1");

        assertThat(decrypted).isEqualTo(PLAINTEXT);
    }

    @Test
    @DisplayName("decrypt — 다른 operatorId (잘못된 AAD) → IllegalStateException")
    void decrypt_wrongOperatorId_throwsIllegalStateException() {
        byte[] encrypted = cipher.encrypt(PLAINTEXT, OPERATOR_ID);

        assertThatThrownBy(() -> cipher.decrypt(encrypted, OPERATOR_ID + 1, "key-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("decrypt — 알 수 없는 keyId → IllegalStateException (Unknown TOTP encryption kid)")
    void decrypt_unknownKeyId_throwsIllegalStateException() {
        byte[] encrypted = cipher.encrypt(PLAINTEXT, OPERATOR_ID);

        assertThatThrownBy(() -> cipher.decrypt(encrypted, OPERATOR_ID, "unknown-kid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown TOTP encryption kid");
    }

    @Test
    @DisplayName("decrypt — blob 길이 부족 → IllegalStateException (too short)")
    void decrypt_tooShortBlob_throwsIllegalStateException() {
        assertThatThrownBy(() -> cipher.decrypt(new byte[]{1, 2, 3}, OPERATOR_ID, "key-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    @DisplayName("init — encryption-keys 비어 있음 → IllegalStateException")
    void init_emptyEncryptionKeys_throwsIllegalStateException() {
        AdminTotpProperties props = new AdminTotpProperties();
        props.setEncryptionKeyId("k");
        props.setEncryptionKeys(Map.of());
        TotpSecretCipher invalid = new TotpSecretCipher(props);

        assertThatThrownBy(invalid::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be empty");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static TotpSecretCipher buildCipher(String keyId, String keyBase64) {
        AdminTotpProperties props = new AdminTotpProperties();
        props.setEncryptionKeyId(keyId);
        props.setEncryptionKeys(Map.of(keyId, keyBase64));
        TotpSecretCipher c = new TotpSecretCipher(props);
        c.init();
        return c;
    }

    private static String randomAes256KeyBase64() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}

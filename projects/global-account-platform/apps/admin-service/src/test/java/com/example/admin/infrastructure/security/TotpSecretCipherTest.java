package com.example.admin.infrastructure.security;

import com.example.admin.infrastructure.config.AdminTotpProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpSecretCipherTest {

    private static final String KEY_V1_B64 = "REVWX09OTFlfVE9UUF9LRVlfMzJfQllURVNfISEhWFg=";
    private static final byte[] PLAINTEXT = new byte[] {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20
    };

    private static TotpSecretCipher newCipher(Map<String, String> keys, String activeKid) {
        AdminTotpProperties props = new AdminTotpProperties();
        props.setEncryptionKeyId(activeKid);
        props.setEncryptionKeys(keys);
        TotpSecretCipher cipher = new TotpSecretCipher(props);
        cipher.init();
        return cipher;
    }

    private static TotpSecretCipher defaultCipher() {
        return newCipher(Map.of("v1", KEY_V1_B64), "v1");
    }

    @Test
    void encryptDecryptRoundTrip() {
        TotpSecretCipher cipher = defaultCipher();
        byte[] blob = cipher.encrypt(PLAINTEXT, 42L);
        byte[] recovered = cipher.decrypt(blob, 42L, "v1");
        assertThat(recovered).containsExactly(PLAINTEXT);
    }

    @Test
    void ivIsRandomisedAcrossWrites() {
        TotpSecretCipher cipher = defaultCipher();
        byte[] a = cipher.encrypt(PLAINTEXT, 42L);
        byte[] b = cipher.encrypt(PLAINTEXT, 42L);
        assertThat(a).isNotEqualTo(b);
        // The first 12 bytes are the IV and must differ.
        for (int i = 0; i < 12; i++) {
            if (a[i] != b[i]) return;
        }
        org.junit.jupiter.api.Assertions.fail("IV was reused across encrypt() calls");
    }

    @Test
    void decryptFailsWhenAadOperatorIdMismatches() {
        TotpSecretCipher cipher = defaultCipher();
        byte[] blob = cipher.encrypt(PLAINTEXT, 42L);
        assertThatThrownBy(() -> cipher.decrypt(blob, 99L, "v1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AES-GCM decrypt failed");
    }

    @Test
    void decryptFailsForUnknownKid() {
        TotpSecretCipher cipher = defaultCipher();
        byte[] blob = cipher.encrypt(PLAINTEXT, 42L);
        assertThatThrownBy(() -> cipher.decrypt(blob, 42L, "nope"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown TOTP encryption kid");
    }

    @Test
    void initRejectsKeyOfWrongLength() {
        // 16 random bytes (AES-128) — not accepted; cipher requires 32 bytes.
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        AdminTotpProperties props = new AdminTotpProperties();
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("v1", shortKey);
        props.setEncryptionKeyId("v1");
        props.setEncryptionKeys(keys);
        TotpSecretCipher cipher = new TotpSecretCipher(props);
        assertThatThrownBy(cipher::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must decode to 32 bytes");
    }

    @Test
    void initRejectsActiveKidNotPresent() {
        AdminTotpProperties props = new AdminTotpProperties();
        props.setEncryptionKeyId("v2");
        props.setEncryptionKeys(Map.of("v1", KEY_V1_B64));
        TotpSecretCipher cipher = new TotpSecretCipher(props);
        assertThatThrownBy(cipher::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption-key-id=v2");
    }
}

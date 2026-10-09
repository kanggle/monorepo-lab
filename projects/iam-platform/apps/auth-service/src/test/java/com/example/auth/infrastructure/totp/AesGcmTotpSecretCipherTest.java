package com.example.auth.infrastructure.totp;

import com.example.auth.application.port.TotpSecretCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("계정 TOTP 비밀 암호화 AES-GCM (TASK-MONO-771 S2b)")
class AesGcmTotpSecretCipherTest {

    private static final String KEY_V1 = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String KEY_V2 = Base64.getEncoder().encodeToString("v2-key-0123456789abcdef-01234567".getBytes());
    private static final byte[] SECRET = "12345678901234567890".getBytes();

    @Test
    @DisplayName("왕복 · 매 쓰기마다 다른 IV · 활성 키 id 가 실린다")
    void roundTrip() {
        AesGcmTotpSecretCipher cipher = new AesGcmTotpSecretCipher("v1", Map.of("v1", KEY_V1));
        TotpSecretCipher.Sealed a = cipher.encrypt(SECRET, "acc-1");
        TotpSecretCipher.Sealed b = cipher.encrypt(SECRET, "acc-1");

        assertThat(a.keyId()).isEqualTo("v1");
        assertThat(a.ciphertext()).isNotEqualTo(b.ciphertext());
        assertThat(a.ciphertext().length).isEqualTo(12 + SECRET.length + 16);
        assertThat(cipher.decrypt(a.ciphertext(), "v1", "acc-1")).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("🔴 AAD = account_id — 다른 계정으로 옮긴 행은 복호화되지 않는다(행 바꿔치기 방어)")
    void rowSwap_failsAuthentication() {
        AesGcmTotpSecretCipher cipher = new AesGcmTotpSecretCipher("v1", Map.of("v1", KEY_V1));
        byte[] sealed = cipher.encrypt(SECRET, "acc-1").ciphertext();

        assertThatThrownBy(() -> cipher.decrypt(sealed, "v1", "acc-2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키 회전: 옛 키 행도 dual-read, 새 쓰기는 활성 키")
    void rotation_dualRead() {
        AesGcmTotpSecretCipher old = new AesGcmTotpSecretCipher("v1", Map.of("v1", KEY_V1));
        byte[] sealedV1 = old.encrypt(SECRET, "acc-1").ciphertext();
        AesGcmTotpSecretCipher rotated = new AesGcmTotpSecretCipher("v2", Map.of("v1", KEY_V1, "v2", KEY_V2));

        assertThat(rotated.decrypt(sealedV1, "v1", "acc-1")).isEqualTo(SECRET);
        assertThat(rotated.encrypt(SECRET, "acc-1").keyId()).isEqualTo("v2");
    }

    @Test
    @DisplayName("잘못된 키 설정은 부팅에서 실패 (첫 등록이 아니라)")
    void badConfig_failsFast() {
        assertThatThrownBy(() -> new AesGcmTotpSecretCipher("v1", Map.of("v1", "c2hvcnQ=")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new AesGcmTotpSecretCipher("v9", Map.of("v1", KEY_V1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("active id");
    }
}

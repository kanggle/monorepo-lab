package com.example.admin.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AdminJwtKeyStore 단위 테스트")
class AdminJwtKeyStoreUnitTest {

    @Test
    @DisplayName("유효한 2048-bit RSA 키 → activeKid/privateKey/publicKey 정상 로드")
    void constructor_validKey_loadsKeyAndDerivedPublicKey() throws Exception {
        String pem = generatePkcs8Pem(2048);
        AdminJwtKeyStore store = new AdminJwtKeyStore(Map.of("kid-1", pem), "kid-1");

        assertThat(store.activeKid()).isEqualTo("kid-1");
        assertThat(store.activePrivateKey()).isNotNull();
        assertThat(store.publicKey("kid-1")).isPresent();
        assertThat(store.publicKeys()).containsKey("kid-1");
    }

    @Test
    @DisplayName("PEM이 빈 문자열 → IllegalStateException (blank PEM)")
    void constructor_blankPem_throwsIllegalStateException() {
        assertThatThrownBy(() -> new AdminJwtKeyStore(Map.of("kid-1", ""), "kid-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("PEM이 유효하지 않은 Base64 → IllegalStateException")
    void constructor_invalidBase64_throwsIllegalStateException() {
        String invalidPem = "-----BEGIN PRIVATE KEY-----\nNOT!VALID!BASE64!!!\n-----END PRIVATE KEY-----";

        assertThatThrownBy(() -> new AdminJwtKeyStore(Map.of("kid-1", invalidPem), "kid-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid base64");
    }

    @Test
    @DisplayName("1024-bit RSA 키 (2048 미만) → IllegalStateException (key too small)")
    void constructor_keyTooSmall_throwsIllegalStateException() throws Exception {
        String pem = generatePkcs8Pem(1024);

        assertThatThrownBy(() -> new AdminJwtKeyStore(Map.of("kid-small", pem), "kid-small"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 2048 bits");
    }

    @Test
    @DisplayName("activeKid가 키 맵에 없음 → IllegalStateException")
    void constructor_activeKidNotInMap_throwsIllegalStateException() throws Exception {
        String pem = generatePkcs8Pem(2048);

        assertThatThrownBy(() -> new AdminJwtKeyStore(Map.of("kid-1", pem), "kid-missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kid-missing");
    }

    @Test
    @DisplayName("키 맵이 비어 있음 → IllegalStateException")
    void constructor_emptyMap_throwsIllegalStateException() {
        assertThatThrownBy(() -> new AdminJwtKeyStore(Map.of(), "kid-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be empty");
    }

    private static String generatePkcs8Pem(int keyBits) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(keyBits);
        byte[] encoded = gen.generateKeyPair().getPrivate().getEncoded();
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END PRIVATE KEY-----";
    }
}

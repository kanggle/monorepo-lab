package com.example.auth.infrastructure.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwksEndpointProvider 단위 테스트")
class JwksEndpointProviderUnitTest {

    @Test
    @DisplayName("RSA 키 → keys 배열 포함, kty/kid/use/alg/n/e 필드 검증")
    void getJwks_rsaKey_returnsCorrectStructure() throws Exception {
        RSAPublicKey publicKey = generateRsaPublicKey();
        JwksEndpointProvider provider = new JwksEndpointProvider(publicKey, "test-kid-1");

        Map<String, Object> jwks = provider.getJwks();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
        assertThat(keys).hasSize(1);

        Map<String, Object> key = keys.get(0);
        assertThat(key)
                .containsEntry("kty", "RSA")
                .containsEntry("kid", "test-kid-1")
                .containsEntry("use", "sig")
                .containsEntry("alg", "RS256")
                .containsKey("n")
                .containsKey("e");
    }

    @Test
    @DisplayName("n/e 값이 Base64Url (패딩 없음) 로 인코딩, leading zero 제거 후 복원된 값이 원본과 일치")
    void getJwks_modulusAndExponent_base64UrlEncodedWithoutLeadingZero() throws Exception {
        RSAPublicKey publicKey = generateRsaPublicKey();
        JwksEndpointProvider provider = new JwksEndpointProvider(publicKey, "kid-2");

        Map<String, Object> jwks = provider.getJwks();
        @SuppressWarnings("unchecked")
        Map<String, Object> key = ((List<Map<String, Object>>) jwks.get("keys")).get(0);

        String n = (String) key.get("n");
        String e = (String) key.get("e");

        // No padding characters (withoutPadding)
        assertThat(n).doesNotContain("=");
        assertThat(e).doesNotContain("=");

        // Decoded value must equal the original modulus/exponent (BigInteger(1, ...) treats as unsigned)
        assertThat(new BigInteger(1, Base64.getUrlDecoder().decode(n)))
                .isEqualTo(publicKey.getModulus());
        assertThat(new BigInteger(1, Base64.getUrlDecoder().decode(e)))
                .isEqualTo(publicKey.getPublicExponent());
    }

    private static RSAPublicKey generateRsaPublicKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return (RSAPublicKey) gen.generateKeyPair().getPublic();
    }
}

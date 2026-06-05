package com.example.auth.infrastructure.jwt;

import com.example.auth.domain.token.TokenPair;
import com.example.security.jwt.Rs256JwtSigner;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code device_id} access-token claim is carried correctly when present
 * and omitted (rather than null-valued) when absent. Spec:
 * specs/contracts/http/auth-api.md "Access Token claims".
 */
class JwtTokenGeneratorDeviceIdTest {

    private static PrivateKey privateKey;
    private static PublicKey publicKey;

    @BeforeAll
    static void loadKeys() throws Exception {
        privateKey = readPrivate("/keys/private.pem");
        publicKey = readPublic("/keys/public.pem");
    }

    private JwtTokenGenerator generator() {
        Rs256JwtSigner signer = new Rs256JwtSigner(privateKey, "test-key");
        return new JwtTokenGenerator(signer, publicKey, "test-iss", 1800L, 604800L);
    }

    @Test
    @DisplayName("device_id claim is present when supplied")
    void deviceIdClaimPresent() {
        TokenPair pair = generator().generateTokenPair("acc-1", "user", "dev-uuid-7");

        Claims claims = Jwts.parser().verifyWith(publicKey).build()
                .parseSignedClaims(pair.accessToken()).getPayload();

        assertThat(claims.get("device_id", String.class)).isEqualTo("dev-uuid-7");
        assertThat(claims.getSubject()).isEqualTo("acc-1");
        assertThat(claims.get("scope", String.class)).isEqualTo("user");
    }

    @Test
    @DisplayName("device_id claim is omitted when null (no null-valued claim)")
    void deviceIdClaimAbsentWhenNull() {
        TokenPair pair = generator().generateTokenPair("acc-2", "user", (String) null);

        Claims claims = Jwts.parser().verifyWith(publicKey).build()
                .parseSignedClaims(pair.accessToken()).getPayload();

        assertThat(claims).doesNotContainKey("device_id");
    }

    @Test
    @DisplayName("default 2-arg overload omits device_id")
    void defaultOverloadOmitsDeviceId() {
        TokenPair pair = generator().generateTokenPair("acc-3", "user");

        Claims claims = Jwts.parser().verifyWith(publicKey).build()
                .parseSignedClaims(pair.accessToken()).getPayload();

        assertThat(claims).doesNotContainKey("device_id");
    }

    private static PrivateKey readPrivate(String path) throws Exception {
        byte[] der = readPem(path);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static PublicKey readPublic(String path) throws Exception {
        byte[] der = readPem(path);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static byte[] readPem(String classpath) throws Exception {
        try (InputStream is = JwtTokenGeneratorDeviceIdTest.class.getResourceAsStream(classpath)) {
            assert is != null;
            String pem = new String(is.readAllBytes())
                    .replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(pem);
        }
    }
}

package com.example.security.jwt;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSignerVerifierTest {

    private static KeyPair keyPair;
    private static KeyPair otherKeyPair;

    @BeforeAll
    static void generateKeys() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
    }

    @Test
    @DisplayName("sign + verify roundtrip succeeds")
    void signAndVerifyRoundtrip() {
        JwtSigner signer = new Rs256JwtSigner(keyPair.getPrivate(), "test-kid-1");
        JwtVerifier verifier = new Rs256JwtVerifier(keyPair.getPublic());

        Instant now = Instant.now();
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "email", "test@example.com",
                "role", "USER",
                "iss", "gap-auth",
                "iat", now,
                "exp", now.plusSeconds(1800)
        );

        String token = signer.sign(claims);
        Map<String, Object> verified = verifier.verify(token);

        assertThat(verified.get("sub")).isEqualTo("user-123");
        assertThat(verified.get("email")).isEqualTo("test@example.com");
        assertThat(verified.get("role")).isEqualTo("USER");
        assertThat(verified.get("iss")).isEqualTo("gap-auth");
    }

    @Test
    @DisplayName("verify fails for expired token")
    void verifyFailsForExpiredToken() {
        JwtSigner signer = new Rs256JwtSigner(keyPair.getPrivate(), "test-kid-1");
        JwtVerifier verifier = new Rs256JwtVerifier(keyPair.getPublic());

        Instant past = Instant.now().minusSeconds(3600);
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "iat", past.minusSeconds(3600),
                "exp", past // already expired
        );

        String token = signer.sign(claims);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtVerificationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("verify fails when signed with different key")
    void verifyFailsWithWrongKey() {
        JwtSigner signer = new Rs256JwtSigner(keyPair.getPrivate(), "test-kid-1");
        JwtVerifier verifier = new Rs256JwtVerifier(otherKeyPair.getPublic());

        Instant now = Instant.now();
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "exp", now.plusSeconds(1800)
        );

        String token = signer.sign(claims);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtVerificationException.class)
                .hasMessageContaining("verification failed");
    }

    @Test
    @DisplayName("sign includes kid in header")
    void signIncludesKidInHeader() {
        JwtSigner signer = new Rs256JwtSigner(keyPair.getPrivate(), "my-kid-42");

        Instant now = Instant.now();
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "exp", now.plusSeconds(1800)
        );

        String token = signer.sign(claims);

        // JWT is three parts separated by dots; header is the first part
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        // Decode header and check kid is present
        String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
        assertThat(headerJson).contains("\"kid\":\"my-kid-42\"");
    }

    @Test
    @DisplayName("sign with jti claim is preserved in verification")
    void signWithJtiClaim() {
        JwtSigner signer = new Rs256JwtSigner(keyPair.getPrivate(), "test-kid-1");
        JwtVerifier verifier = new Rs256JwtVerifier(keyPair.getPublic());

        Instant now = Instant.now();
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "jti", "unique-token-id-456",
                "exp", now.plusSeconds(1800)
        );

        String token = signer.sign(claims);
        Map<String, Object> verified = verifier.verify(token);

        assertThat(verified.get("jti")).isEqualTo("unique-token-id-456");
    }
}

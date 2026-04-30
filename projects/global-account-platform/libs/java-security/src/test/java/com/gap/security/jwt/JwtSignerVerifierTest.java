package com.gap.security.jwt;

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

    @Test
    @DisplayName("verify passes when iss claim matches expectedIssuer (TASK-BE-143)")
    void verifyPassesWithMatchingIssuer() {
        JwtSigner signer = new Rs256JwtSigner(keyPair.getPrivate(), "test-kid-1");
        JwtVerifier verifier = new Rs256JwtVerifier(keyPair.getPublic(), "global-account-platform");

        Instant now = Instant.now();
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "iss", "global-account-platform",
                "exp", now.plusSeconds(1800)
        );

        String token = signer.sign(claims);
        Map<String, Object> verified = verifier.verify(token);

        assertThat(verified.get("iss")).isEqualTo("global-account-platform");
    }

    @Test
    @DisplayName("verify fails when iss claim differs from expectedIssuer (TASK-BE-143)")
    void verifyFailsWithMismatchedIssuer() {
        JwtSigner signer = new Rs256JwtSigner(keyPair.getPrivate(), "test-kid-1");
        JwtVerifier verifier = new Rs256JwtVerifier(keyPair.getPublic(), "global-account-platform");

        Instant now = Instant.now();
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "iss", "attacker-issuer",
                "exp", now.plusSeconds(1800)
        );

        String token = signer.sign(claims);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtVerificationException.class);
    }

    @Test
    @DisplayName("verify fails when iss claim is missing (TASK-BE-143)")
    void verifyFailsWithMissingIssuer() {
        JwtSigner signer = new Rs256JwtSigner(keyPair.getPrivate(), "test-kid-1");
        JwtVerifier verifier = new Rs256JwtVerifier(keyPair.getPublic(), "global-account-platform");

        Instant now = Instant.now();
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "exp", now.plusSeconds(1800)
        );

        String token = signer.sign(claims);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtVerificationException.class);
    }

    @Test
    @DisplayName("verify ignores iss when expectedIssuer is null (backward compat)")
    void verifyIgnoresIssuerWhenNull() {
        JwtSigner signer = new Rs256JwtSigner(keyPair.getPrivate(), "test-kid-1");
        JwtVerifier verifier = new Rs256JwtVerifier(keyPair.getPublic(), null);

        Instant now = Instant.now();
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "iss", "any-issuer",
                "exp", now.plusSeconds(1800)
        );

        String token = signer.sign(claims);
        Map<String, Object> verified = verifier.verify(token);

        assertThat(verified.get("iss")).isEqualTo("any-issuer");
    }

    @Test
    @DisplayName("verify enforces aud claim when expectedAudience is set (TASK-BE-143)")
    void verifyAudienceClaim() {
        JwtSigner signer = new Rs256JwtSigner(keyPair.getPrivate(), "test-kid-1");
        JwtVerifier verifier = new Rs256JwtVerifier(
                keyPair.getPublic(), "global-account-platform", "community-service");

        Instant now = Instant.now();
        // matching audience passes
        Map<String, Object> matchingClaims = Map.of(
                "sub", "user-123",
                "iss", "global-account-platform",
                "aud", "community-service",
                "exp", now.plusSeconds(1800)
        );
        Map<String, Object> verified = verifier.verify(signer.sign(matchingClaims));
        // JJWT 0.12.x normalizes aud claim to a list per RFC 7519
        assertThat(verified.get("aud").toString()).contains("community-service");

        // mismatched audience fails
        Map<String, Object> wrongAud = Map.of(
                "sub", "user-123",
                "iss", "global-account-platform",
                "aud", "admin-service",
                "exp", now.plusSeconds(1800)
        );
        String wrongToken = signer.sign(wrongAud);
        assertThatThrownBy(() -> verifier.verify(wrongToken))
                .isInstanceOf(JwtVerificationException.class);
    }
}

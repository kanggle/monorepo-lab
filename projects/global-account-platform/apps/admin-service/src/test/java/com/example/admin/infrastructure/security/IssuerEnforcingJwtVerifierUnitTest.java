package com.example.admin.infrastructure.security;

import com.example.security.jwt.JwtVerificationException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IssuerEnforcingJwtVerifier 단위 테스트")
class IssuerEnforcingJwtVerifierUnitTest {

    private static final String EXPECTED_ISSUER = "https://admin.example.com";

    private PrivateKey privateKey;
    private IssuerEnforcingJwtVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        privateKey = kp.getPrivate();
        verifier = new IssuerEnforcingJwtVerifier((RSAPublicKey) kp.getPublic(), EXPECTED_ISSUER);
    }

    @Test
    @DisplayName("서명 유효 + iss 일치 → claims 반환")
    void verify_validTokenMatchingIssuer_returnsClaims() {
        Map<String, Object> claims = verifier.verify(signedToken(EXPECTED_ISSUER));

        assertThat(claims.get("iss")).isEqualTo(EXPECTED_ISSUER);
    }

    @Test
    @DisplayName("서명 유효 + iss 불일치 → JwtVerificationException")
    void verify_wrongIssuer_throwsJwtVerificationException() {
        assertThatThrownBy(() -> verifier.verify(signedToken("https://attacker.example.com")))
                .isInstanceOf(JwtVerificationException.class);
    }

    @Test
    @DisplayName("서명 변조 → JwtVerificationException")
    void verify_tamperedSignature_throwsJwtVerificationException() {
        String token = signedToken(EXPECTED_ISSUER);
        // Replace last 4 characters of the signature to corrupt it
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThatThrownBy(() -> verifier.verify(tampered))
                .isInstanceOf(JwtVerificationException.class);
    }

    private String signedToken(String issuer) {
        return Jwts.builder()
                .issuer(issuer)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(privateKey)
                .compact();
    }
}

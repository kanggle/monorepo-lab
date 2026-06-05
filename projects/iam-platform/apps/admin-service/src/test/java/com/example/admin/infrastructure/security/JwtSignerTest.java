package com.example.admin.infrastructure.security;

import com.example.security.jwt.JwtVerifier;
import com.example.security.jwt.Rs256JwtVerifier;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSignerTest {

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private static String toPkcs8Pem(java.security.PrivateKey pk) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    @Test
    void sign_produces_verifiable_jws_with_kid_header_and_claims() throws Exception {
        KeyPair kp = rsaKeyPair();
        AdminJwtKeyStore keyStore = new AdminJwtKeyStore(
                Map.of("v1", toPkcs8Pem(kp.getPrivate())), "v1");

        JwtSigner signer = new JwtSigner(keyStore, "admin-service");

        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "00000000-0000-7000-8000-000000000001");
        claims.put("token_type", "admin");
        claims.put("jti", "abc-123");
        claims.put("iat", now);
        claims.put("exp", now.plus(10, ChronoUnit.MINUTES));

        String jws = signer.sign(claims);
        assertThat(jws.split("\\.")).hasSize(3);

        // Header contains alg=RS256 + kid=v1.
        String headerJson = new String(Base64.getUrlDecoder().decode(jws.split("\\.")[0]));
        assertThat(headerJson).contains("\"alg\":\"RS256\"");
        assertThat(headerJson).contains("\"kid\":\"v1\"");

        // Public key derived from the same keypair verifies the signature;
        // iss is auto-injected and claims round-trip.
        JwtVerifier verifier = new Rs256JwtVerifier(kp.getPublic());
        Map<String, Object> parsed = verifier.verify(jws);
        assertThat(parsed).containsEntry("sub", "00000000-0000-7000-8000-000000000001");
        assertThat(parsed).containsEntry("token_type", "admin");
        assertThat(parsed).containsEntry("iss", "admin-service");
        assertThat(parsed).containsEntry("jti", "abc-123");

        // Independent JJWT parse also confirms the kid header, hardening
        // against any future drift in the verifier implementation.
        var header = Jwts.parser().verifyWith(kp.getPublic()).build()
                .parseSignedClaims(jws).getHeader();
        assertThat(header.getKeyId()).isEqualTo("v1");
    }

    @Test
    void active_kid_matches_configured_value() throws Exception {
        KeyPair kp = rsaKeyPair();
        AdminJwtKeyStore keyStore = new AdminJwtKeyStore(
                Map.of("v7", toPkcs8Pem(kp.getPrivate())), "v7");
        JwtSigner signer = new JwtSigner(keyStore, "admin-service");
        assertThat(signer.activeKid()).isEqualTo("v7");
    }
}

package com.example.admin.infrastructure.security;

import com.example.admin.application.exception.SubjectTokenInvalidException;
import com.example.admin.infrastructure.config.IamOidcProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * TASK-BE-298 / ADR-MONO-014 — unit coverage for
 * {@link IamOidcJwksSubjectTokenValidator}: signature, issuer, audience,
 * expiry, the "no {@code token_type} claim" GAP-OIDC-shape guard, missing
 * {@code sub}, and fail-closed JWKS-unreachable. A WireMock server serves the
 * auth-service JWKS; the validator key space is exercised end to end without
 * Docker.
 */
@DisplayName("IamOidcJwksSubjectTokenValidator (subject-token validation branches)")
class IamOidcJwksSubjectTokenValidatorTest {

    private static final String ISSUER = "http://localhost:8081";
    private static final String AUDIENCE = "platform-console-web";
    private static final String KID = "auth-kid-1";

    private WireMockServer wireMock;
    private KeyPair authKeyPair;
    private IamOidcJwksSubjectTokenValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        authKeyPair = gen.generateKeyPair();

        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        stubJwks((RSAPublicKey) authKeyPair.getPublic(), KID);

        IamOidcProperties props = new IamOidcProperties();
        props.setJwksUri(wireMock.baseUrl() + "/internal/auth/jwks");
        props.setIssuer(ISSUER);
        props.setAudience(AUDIENCE);
        props.setClockSkewSeconds(60);
        validator = new IamOidcJwksSubjectTokenValidator(props, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) wireMock.stop();
    }

    private void stubJwks(RSAPublicKey key, String kid) {
        String n = b64Url(key.getModulus().toByteArray());
        String e = b64Url(key.getPublicExponent().toByteArray());
        String body = "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\""
                + kid + "\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/auth/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static String b64Url(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] s = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, s, 0, s.length);
            bytes = s;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private JwtBuilder baseToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(KID).and()
                .subject("acc-uuid-0001")
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)));
    }

    @Test
    @DisplayName("valid GAP OIDC access token → returns the sub")
    void validToken_returnsSubject() {
        String token = baseToken().signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
        assertThat(validator.validateAndExtractSubject(token)).isEqualTo("acc-uuid-0001");
    }

    @Test
    @DisplayName("wrong signature (different key) → 401 fail-closed")
    void wrongSignature_rejected() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair attacker = gen.generateKeyPair();
        String token = baseToken().signWith(attacker.getPrivate(), Jwts.SIG.RS256).compact();
        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> validator.validateAndExtractSubject(token));
    }

    @Test
    @DisplayName("wrong issuer → 401")
    void wrongIssuer_rejected() {
        String token = baseToken().issuer("https://evil.example.com")
                .signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> validator.validateAndExtractSubject(token));
    }

    @Test
    @DisplayName("wrong audience (token issued to another client) → 401")
    void wrongAudience_rejected() {
        String token = Jwts.builder()
                .header().keyId(KID).and()
                .subject("acc-uuid-0001")
                .issuer(ISSUER)
                .audience().add("some-other-client").and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> validator.validateAndExtractSubject(token));
    }

    @Test
    @DisplayName("expired token (beyond clock skew) → 401")
    void expiredToken_rejected() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        String token = Jwts.builder()
                .header().keyId(KID).and()
                .subject("acc-uuid-0001")
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(past))
                .expiration(Date.from(past.plus(5, ChronoUnit.MINUTES)))
                .signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> validator.validateAndExtractSubject(token));
    }

    @Test
    @DisplayName("token carrying a token_type claim (= admin-minted, not GAP OIDC) → 401")
    void tokenWithTokenTypeClaim_rejected() {
        // An attacker presenting an admin-service-minted operator token as a
        // subject token must be rejected (security.md validation #5).
        String token = baseToken().claim("token_type", "admin")
                .signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> validator.validateAndExtractSubject(token));
    }

    @Test
    @DisplayName("missing sub claim → 401")
    void missingSubject_rejected() {
        String token = Jwts.builder()
                .header().keyId(KID).and()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> validator.validateAndExtractSubject(token));
    }

    @Test
    @DisplayName("blank/null subject token → 401")
    void blankToken_rejected() {
        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> validator.validateAndExtractSubject("  "));
        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> validator.validateAndExtractSubject(null));
    }

    @Test
    @DisplayName("auth-service JWKS unreachable → 401 fail-closed (token NOT trusted unverified)")
    void jwksUnreachable_failClosed() {
        wireMock.stop(); // JWKS endpoint now unreachable
        String token = baseToken().signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> validator.validateAndExtractSubject(token));
    }

    @Test
    @DisplayName("unknown kid (not in JWKS) → 401")
    void unknownKid_rejected() {
        String token = Jwts.builder()
                .header().keyId("totally-unknown-kid").and()
                .subject("acc-uuid-0001")
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> validator.validateAndExtractSubject(token));
    }
}

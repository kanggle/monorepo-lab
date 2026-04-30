package com.example.auth.infrastructure.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcJwksVerifierTest {

    private static final String JWKS_PATH = "/oauth2/v3/certs";
    private static final String AUDIENCE = "test-client-id";
    private static final String ISSUER_PATTERN = "^(https://)?accounts\\.google\\.com$";
    private static final String CONFIGURED_KID = "test-kid-1";
    private static final String OTHER_KID = "test-kid-2";

    private static KeyPair primaryKp;
    private static KeyPair secondaryKp;
    private static KeyPair attackerKp;

    private WireMockServer wireMockServer;
    private OidcJwksVerifier verifier;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        primaryKp = gen.generateKeyPair();
        secondaryKp = gen.generateKeyPair();
        attackerKp = gen.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();

        verifier = new OidcJwksVerifier(
                wireMockServer.baseUrl() + JWKS_PATH,
                ISSUER_PATTERN,
                AUDIENCE,
                RestClient.builder().build(),
                new ObjectMapper(),
                3_600_000L);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("정상 서명 + iss + aud + exp 모두 만족 → claims 반환")
    void verify_validToken_returnsClaims() {
        stubJwksWith(CONFIGURED_KID, (RSAPublicKey) primaryKp.getPublic());

        String token = signToken(primaryKp, CONFIGURED_KID, "https://accounts.google.com", AUDIENCE,
                Instant.now().plusSeconds(600));

        Map<String, Object> claims = verifier.verify(token);

        assertThat(claims.get("sub")).isEqualTo("user-1");
        assertThat(claims.get("iss")).isEqualTo("https://accounts.google.com");
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰 → signature 검증 실패")
    void verify_wrongSignature_throws() {
        // JWKS publishes the primary public key, but token is signed by attacker's private key.
        stubJwksWith(CONFIGURED_KID, (RSAPublicKey) primaryKp.getPublic());

        String forged = signToken(attackerKp, CONFIGURED_KID, "https://accounts.google.com", AUDIENCE,
                Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> verifier.verify(forged))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("issuer 가 패턴과 일치하지 않으면 거부")
    void verify_wrongIssuer_throws() {
        stubJwksWith(CONFIGURED_KID, (RSAPublicKey) primaryKp.getPublic());

        String token = signToken(primaryKp, CONFIGURED_KID, "https://attacker.example.com", AUDIENCE,
                Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    @DisplayName("audience 가 client_id 와 다르면 거부")
    void verify_wrongAudience_throws() {
        stubJwksWith(CONFIGURED_KID, (RSAPublicKey) primaryKp.getPublic());

        String token = signToken(primaryKp, CONFIGURED_KID, "https://accounts.google.com",
                "different-client-id",
                Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("audience");
    }

    @Test
    @DisplayName("만료된 토큰은 거부")
    void verify_expired_throws() {
        stubJwksWith(CONFIGURED_KID, (RSAPublicKey) primaryKp.getPublic());

        String token = signToken(primaryKp, CONFIGURED_KID, "https://accounts.google.com", AUDIENCE,
                Instant.now().minusSeconds(3600));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("kid 가 JWKS 에 없으면 강제 refresh 후 재시도, 그래도 없으면 실패")
    void verify_kidNotInJwks_throwsAfterRefresh() {
        stubJwksWith(CONFIGURED_KID, (RSAPublicKey) primaryKp.getPublic());

        String token = signToken(primaryKp, "unknown-kid", "https://accounts.google.com", AUDIENCE,
                Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("kid");
    }

    @Test
    @DisplayName("kid mismatch 후 refresh 로 새 키가 추가되면 검증 성공")
    void verify_kidAddedAfterRefresh_succeeds() {
        // First serve JWKS with only the primary key
        stubJwksWith(CONFIGURED_KID, (RSAPublicKey) primaryKp.getPublic());

        // Token signed with secondary key + new kid → first lookup misses, force refresh fires
        String token = signToken(secondaryKp, OTHER_KID, "https://accounts.google.com", AUDIENCE,
                Instant.now().plusSeconds(600));

        // Reconfigure JWKS endpoint to publish both keys (simulates kid rotation at provider)
        wireMockServer.resetAll();
        stubJwksWithTwo(CONFIGURED_KID, (RSAPublicKey) primaryKp.getPublic(),
                OTHER_KID, (RSAPublicKey) secondaryKp.getPublic());

        Map<String, Object> claims = verifier.verify(token);

        assertThat(claims.get("sub")).isEqualTo("user-1");
    }

    @Test
    @DisplayName("JWKS 엔드포인트 장애 + 캐시 비어있음 → OAuthProviderException 전파")
    void verify_jwksUnreachableFirstLoad_throws() {
        wireMockServer.stubFor(get(urlEqualTo(JWKS_PATH))
                .willReturn(aResponse().withStatus(503)));

        String token = signToken(primaryKp, CONFIGURED_KID, "https://accounts.google.com", AUDIENCE,
                Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OAuthProviderException.class);
    }

    @Test
    @DisplayName("alg:none 토큰 거부 (algorithm confusion 방어)")
    void verify_algNoneToken_throws() {
        stubJwksWith(CONFIGURED_KID, (RSAPublicKey) primaryKp.getPublic());

        // Hand-craft alg:none token: header.payload. (empty signature segment)
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"alg\":\"none\",\"typ\":\"JWT\",\"kid\":\""
                        + CONFIGURED_KID + "\"}").getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"attacker\",\"iss\":\"https://accounts.google.com\","
                        + "\"aud\":\"" + AUDIENCE + "\",\"exp\":9999999999}").getBytes());
        String unsecuredToken = header + "." + payload + ".";

        assertThatThrownBy(() -> verifier.verify(unsecuredToken))
                .isInstanceOf(OAuthProviderException.class);
    }

    @Test
    @DisplayName("alg:HS256 토큰 거부 (HMAC-with-public-key confusion 방어)")
    void verify_algHs256Token_throws() {
        stubJwksWith(CONFIGURED_KID, (RSAPublicKey) primaryKp.getPublic());

        // Hand-craft an HS256-style token signed with the public key as HMAC secret.
        // The verifier MUST reject this — the explicit alg=RS256 pin in
        // JwksKeyLocator is the primary safety net (defense-in-depth on top of
        // JJWT's implicit type-mismatch guard).
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            byte[] keyBytes = ((RSAPublicKey) primaryKp.getPublic()).getEncoded();
            mac.init(new javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA256"));

            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(("{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\""
                            + CONFIGURED_KID + "\"}").getBytes());
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(("{\"sub\":\"attacker\",\"iss\":\"https://accounts.google.com\","
                            + "\"aud\":\"" + AUDIENCE + "\",\"exp\":9999999999}").getBytes());
            String signingInput = header + "." + payload;
            byte[] signature = mac.doFinal(signingInput.getBytes());
            String token = signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

            assertThatThrownBy(() -> verifier.verify(token))
                    .isInstanceOf(OAuthProviderException.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("정상 발급 토큰의 kid 가 비어 있으면 거부 (header bypass 방지)")
    void verify_missingKid_throws() {
        stubJwksWith(CONFIGURED_KID, (RSAPublicKey) primaryKp.getPublic());

        // Sign without kid header
        String token = Jwts.builder()
                .subject("user-1")
                .issuer("https://accounts.google.com")
                .audience().add(AUDIENCE).and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(primaryKp.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("kid");
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private void stubJwksWith(String kid, RSAPublicKey key) {
        wireMockServer.stubFor(get(urlEqualTo(JWKS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwksJson(kid, key))));
    }

    private void stubJwksWithTwo(String kid1, RSAPublicKey k1, String kid2, RSAPublicKey k2) {
        String body = """
                {"keys":[%s,%s]}
                """.formatted(jwkEntry(kid1, k1), jwkEntry(kid2, k2));
        wireMockServer.stubFor(get(urlEqualTo(JWKS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static String jwksJson(String kid, RSAPublicKey key) {
        return "{\"keys\":[" + jwkEntry(kid, key) + "]}";
    }

    private static String jwkEntry(String kid, RSAPublicKey key) {
        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedByteArray(key.getModulus()));
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedByteArray(key.getPublicExponent()));
        return "{\"kty\":\"RSA\",\"kid\":\"" + kid + "\",\"alg\":\"RS256\",\"use\":\"sig\","
                + "\"n\":\"" + n + "\",\"e\":\"" + e + "\"}";
    }

    private static byte[] toUnsignedByteArray(java.math.BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes[0] == 0x00) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    private static String signToken(KeyPair kp, String kid, String iss, String aud, Instant exp) {
        return Jwts.builder()
                .header().keyId(kid).and()
                .subject("user-1")
                .issuer(iss)
                .audience().add(aud).and()
                .issuedAt(new Date())
                .expiration(Date.from(exp))
                .signWith(kp.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}

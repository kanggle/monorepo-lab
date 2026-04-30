package com.example.auth.infrastructure.oauth;

import com.example.auth.domain.oauth.OAuthProvider;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleOAuthClientTest {

    private static final String REDIRECT_URI = "http://localhost:3000/oauth/callback";
    private static final String CLIENT_ID = "test-google-client-id";
    private static final String JWKS_PATH = "/oauth2/v3/certs";
    private static final String TOKEN_PATH = "/oauth2/v4/token";
    private static final String KID = "google-kid-1";
    private static final String ISSUER_PATTERN = "^(https://)?accounts\\.google\\.com$";
    private static final String ISSUER_VALUE = "https://accounts.google.com";

    private static KeyPair providerKp;
    private static KeyPair attackerKp;

    private WireMockServer wireMockServer;
    private GoogleOAuthClient client;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        providerKp = gen.generateKeyPair();
        attackerKp = gen.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();

        OAuthProperties props = new OAuthProperties();
        OAuthProperties.ProviderProperties googleProps = props.getGoogle();
        googleProps.setClientId(CLIENT_ID);
        googleProps.setClientSecret("test-google-client-secret");
        googleProps.setTokenUri(wireMockServer.baseUrl() + TOKEN_PATH);
        googleProps.setJwksUri(wireMockServer.baseUrl() + JWKS_PATH);
        googleProps.setExpectedIssuerPattern(ISSUER_PATTERN);
        googleProps.setJwksCacheTtlMillis(3_600_000L);

        stubJwks((RSAPublicKey) providerKp.getPublic());

        client = new GoogleOAuthClient(props, new ObjectMapper(), RestClient.builder().build());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("정상 서명 + iss + aud → OAuthUserInfo 반환")
    void happyPath() {
        String idToken = signedIdToken("google-user-001", "alice@gmail.com", "Alice");
        stubTokenEndpoint(idToken);

        OAuthUserInfo info = client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI);

        assertThat(info.providerUserId()).isEqualTo("google-user-001");
        assertThat(info.email()).isEqualTo("alice@gmail.com");
        assertThat(info.name()).isEqualTo("Alice");
        assertThat(info.provider()).isEqualTo(OAuthProvider.GOOGLE);
    }

    @Test
    @DisplayName("token 응답에 id_token 누락 → OAuthProviderException")
    void tokenResponseMissingIdToken() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"abc\"}")));

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("missing id_token");
    }

    @Test
    @DisplayName("id_token 의 sub claim 누락 → OAuthProviderException")
    void idTokenMissingSub() {
        // sub-less claims still pass JWKS verify (iss/aud/exp valid), but client-level guard rejects
        String idToken = Jwts.builder()
                .header().keyId(KID).and()
                .issuer(ISSUER_VALUE)
                .audience().add(CLIENT_ID).and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("email", "x@y.com")
                .signWith(providerKp.getPrivate(), Jwts.SIG.RS256)
                .compact();
        stubTokenEndpoint(idToken);

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("missing 'sub'");
    }

    @Test
    @DisplayName("token endpoint 5xx → OAuthProviderException")
    void tokenEndpoint5xx() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class);
    }

    @Test
    @DisplayName("TASK-BE-145 — 잘못된 키로 서명된 id_token 거부 (서명 검증)")
    void verify_forgedSignature_rejected() {
        String forged = Jwts.builder()
                .header().keyId(KID).and()
                .subject("attacker-sub")
                .issuer(ISSUER_VALUE)
                .audience().add(CLIENT_ID).and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(attackerKp.getPrivate(), Jwts.SIG.RS256)
                .compact();
        stubTokenEndpoint(forged);

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class);
    }

    @Test
    @DisplayName("TASK-BE-145 — Google 이 아닌 iss 의 id_token 거부")
    void verify_wrongIssuer_rejected() {
        String wrongIss = Jwts.builder()
                .header().keyId(KID).and()
                .subject("user-1")
                .issuer("https://login.microsoftonline.com/x/v2.0")
                .audience().add(CLIENT_ID).and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(providerKp.getPrivate(), Jwts.SIG.RS256)
                .compact();
        stubTokenEndpoint(wrongIss);

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class);
    }

    @Test
    @DisplayName("TASK-BE-145 — aud 가 다른 client_id 인 id_token 거부 (token confusion 차단)")
    void verify_wrongAudience_rejected() {
        String wrongAud = Jwts.builder()
                .header().keyId(KID).and()
                .subject("user-1")
                .issuer(ISSUER_VALUE)
                .audience().add("different-app-client-id").and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(providerKp.getPrivate(), Jwts.SIG.RS256)
                .compact();
        stubTokenEndpoint(wrongAud);

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class);
    }

    @Test
    @DisplayName("TASK-BE-145 — Google 의 'accounts.google.com' (스킴 없는 iss) 도 통과")
    void verify_issuerWithoutScheme_passes() {
        String token = Jwts.builder()
                .header().keyId(KID).and()
                .subject("user-2")
                .issuer("accounts.google.com")
                .audience().add(CLIENT_ID).and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("email", "x@gmail.com")
                .signWith(providerKp.getPrivate(), Jwts.SIG.RS256)
                .compact();
        stubTokenEndpoint(token);

        OAuthUserInfo info = client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI);
        assertThat(info.providerUserId()).isEqualTo("user-2");
    }

    @Test
    @DisplayName("TASK-BE-145 — 만료된 id_token 거부")
    void verify_expired_rejected() {
        String expired = Jwts.builder()
                .header().keyId(KID).and()
                .subject("user-1")
                .issuer(ISSUER_VALUE)
                .audience().add(CLIENT_ID).and()
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(providerKp.getPrivate(), Jwts.SIG.RS256)
                .compact();
        stubTokenEndpoint(expired);

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void stubTokenEndpoint(String idToken) {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id_token\":\"" + idToken + "\",\"access_token\":\"google-access-token\"}")));
    }

    private void stubJwks(RSAPublicKey key) {
        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedByteArray(key.getModulus()));
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedByteArray(key.getPublicExponent()));
        String body = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + KID + "\",\"alg\":\"RS256\",\"use\":\"sig\","
                + "\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";
        wireMockServer.stubFor(get(urlEqualTo(JWKS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private String signedIdToken(String sub, String email, String name) {
        return Jwts.builder()
                .header().keyId(KID).and()
                .subject(sub)
                .issuer(ISSUER_VALUE)
                .audience().add(CLIENT_ID).and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("email", email)
                .claim("name", name)
                .signWith(providerKp.getPrivate(), Jwts.SIG.RS256)
                .compact();
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
}

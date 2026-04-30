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

class MicrosoftOAuthClientTest {

    private static final String REDIRECT_URI = "http://localhost:3000/oauth/callback";
    private static final String CLIENT_ID = "test-client-id";
    private static final String JWKS_PATH = "/discovery/v2.0/keys";
    private static final String TOKEN_PATH = "/oauth2/v2.0/token";
    private static final String KID = "test-kid-1";
    private static final String ISSUER_PATTERN = "^https://login\\.microsoftonline\\.com/[^/]+/v2\\.0$";
    private static final String ISSUER_VALUE =
            "https://login.microsoftonline.com/72f988bf-86f1-41af-91ab-2d7cd011db47/v2.0";

    private static KeyPair providerKp;
    private static KeyPair attackerKp;

    private WireMockServer wireMockServer;
    private MicrosoftOAuthClient client;

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
        OAuthProperties.ProviderProperties msProps = props.getMicrosoft();
        msProps.setClientId(CLIENT_ID);
        msProps.setClientSecret("test-client-secret");
        msProps.setTokenUri(wireMockServer.baseUrl() + TOKEN_PATH);
        msProps.setJwksUri(wireMockServer.baseUrl() + JWKS_PATH);
        msProps.setExpectedIssuerPattern(ISSUER_PATTERN);
        msProps.setJwksCacheTtlMillis(3_600_000L);

        stubJwks((RSAPublicKey) providerKp.getPublic());

        client = new MicrosoftOAuthClient(props, new ObjectMapper(), RestClient.builder().build());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("정상 서명 + iss + aud → OAuthUserInfo 반환")
    void happyPath() {
        String idToken = signedIdToken("ms-user-123", "alice@contoso.com", null, "Alice");
        stubTokenEndpoint(idToken);

        OAuthUserInfo info = client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI);

        assertThat(info.providerUserId()).isEqualTo("ms-user-123");
        assertThat(info.email()).isEqualTo("alice@contoso.com");
        assertThat(info.name()).isEqualTo("Alice");
        assertThat(info.provider()).isEqualTo(OAuthProvider.MICROSOFT);
    }

    @Test
    @DisplayName("email 누락 시 preferred_username 으로 폴백")
    void emailFallbackToPreferredUsername() {
        String idToken = signedIdToken("ms-user-456", null, "bob@fabrikam.com", "Bob");
        stubTokenEndpoint(idToken);

        OAuthUserInfo info = client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI);

        assertThat(info.email()).isEqualTo("bob@fabrikam.com");
    }

    @Test
    @DisplayName("email + preferred_username 둘 다 누락 시 email=null")
    void emailNullWhenBothMissing() {
        String idToken = signedIdToken("ms-user-789", null, null, "Carol");
        stubTokenEndpoint(idToken);

        OAuthUserInfo info = client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI);

        assertThat(info.email()).isNull();
        assertThat(info.providerUserId()).isEqualTo("ms-user-789");
    }

    @Test
    @DisplayName("preferred_username 에 @ 없으면 email 폴백 안 함 (UPN)")
    void preferredUsernameWithoutAtIsIgnored() {
        String idToken = signedIdToken("ms-user-000", null, "external-user#EXT", "Dave");
        stubTokenEndpoint(idToken);

        OAuthUserInfo info = client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI);

        assertThat(info.email()).isNull();
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
    @DisplayName("token endpoint 5xx → OAuthProviderException")
    void tokenEndpoint5xx() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("Microsoft OAuth provider error");
    }

    @Test
    @DisplayName("id_token 형식 불량 (점 분리 안됨) → OAuthProviderException")
    void malformedIdToken() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id_token\":\"notajwt\"}")));

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class);
    }

    @Test
    @DisplayName("TASK-BE-145 — 잘못된 키로 서명된 id_token 거부")
    void verify_forgedSignature_rejected() {
        // Token signed with attacker's key, JWKS still publishes provider's key
        String forged = Jwts.builder()
                .header().keyId(KID).and()
                .subject("ms-user-attacker")
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
    @DisplayName("TASK-BE-145 — iss 가 Microsoft 패턴과 다른 id_token 거부")
    void verify_wrongIssuer_rejected() {
        String wrongIss = Jwts.builder()
                .header().keyId(KID).and()
                .subject("ms-user-1")
                .issuer("https://accounts.google.com")
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
    @DisplayName("TASK-BE-145 — aud 가 client_id 와 다른 id_token 거부")
    void verify_wrongAudience_rejected() {
        String wrongAud = Jwts.builder()
                .header().keyId(KID).and()
                .subject("ms-user-1")
                .issuer(ISSUER_VALUE)
                .audience().add("other-client-id").and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(providerKp.getPrivate(), Jwts.SIG.RS256)
                .compact();
        stubTokenEndpoint(wrongAud);

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class);
    }

    @Test
    @DisplayName("TASK-BE-145 — 만료된 id_token 거부")
    void verify_expired_rejected() {
        String expired = Jwts.builder()
                .header().keyId(KID).and()
                .subject("ms-user-1")
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
                        .withBody("{\"id_token\":\"" + idToken + "\",\"access_token\":\"ms-access-token\"}")));
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

    private String signedIdToken(String sub, String email, String preferredUsername, String name) {
        var builder = Jwts.builder()
                .header().keyId(KID).and()
                .subject(sub)
                .issuer(ISSUER_VALUE)
                .audience().add(CLIENT_ID).and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)));
        if (email != null) {
            builder.claim("email", email);
        }
        if (preferredUsername != null) {
            builder.claim("preferred_username", preferredUsername);
        }
        if (name != null) {
            builder.claim("name", name);
        }
        return builder.signWith(providerKp.getPrivate(), Jwts.SIG.RS256).compact();
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

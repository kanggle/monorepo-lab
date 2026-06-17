package com.example.auth.infrastructure.oauth;

import com.example.auth.application.exception.OAuthProviderException;
import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.oauth.OAuthUserInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link NaverOAuthClient} (TASK-BE-397, ADR-006).
 *
 * <p>Naver is a non-OIDC OAuth2 provider (no id_token / JWKS) — the client reads the
 * profile from the user-info endpoint's {@code response} wrapper. WireMock stubs both
 * the token and user-info endpoints (the client builds its own RestClient and issues
 * absolute requests to the configured URIs).
 */
class NaverOAuthClientTest {

    private static final String REDIRECT_URI = "http://iam.local/login/oauth/naver/callback";
    private static final String TOKEN_PATH = "/oauth2.0/token";
    private static final String USERINFO_PATH = "/v1/nid/me";

    private WireMockServer wireMockServer;
    private NaverOAuthClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();

        OAuthProperties props = new OAuthProperties();
        OAuthProperties.ProviderProperties naver = props.getNaver();
        naver.setClientId("test-naver-client-id");
        naver.setClientSecret("test-naver-client-secret");
        naver.setTokenUri(wireMockServer.baseUrl() + TOKEN_PATH);
        naver.setUserInfoUri(wireMockServer.baseUrl() + USERINFO_PATH);

        client = new NaverOAuthClient(props, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("정상 token + userinfo(response 래퍼) → OAuthUserInfo(NAVER) 반환")
    void happyPath() {
        stubToken("{\"access_token\":\"naver-access-token\"}");
        wireMockServer.stubFor(get(urlEqualTo(USERINFO_PATH))
                .willReturn(json("{\"resultcode\":\"00\",\"message\":\"success\","
                        + "\"response\":{\"id\":\"naver-001\",\"email\":\"user@naver.com\","
                        + "\"name\":\"네이버유저\",\"nickname\":\"nick\"}}")));

        OAuthUserInfo info = client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI);

        assertThat(info.providerUserId()).isEqualTo("naver-001");
        assertThat(info.email()).isEqualTo("user@naver.com");
        assertThat(info.name()).isEqualTo("네이버유저");
        assertThat(info.provider()).isEqualTo(OAuthProvider.NAVER);
    }

    @Test
    @DisplayName("token 응답에 access_token 누락 → OAuthProviderException")
    void tokenMissingAccessToken() {
        stubToken("{\"token_type\":\"bearer\"}");

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("missing access_token");
    }

    @Test
    @DisplayName("userinfo resultcode 비정상 → OAuthProviderException")
    void userInfoAbnormalResultCode() {
        stubToken("{\"access_token\":\"naver-access-token\"}");
        wireMockServer.stubFor(get(urlEqualTo(USERINFO_PATH))
                .willReturn(json("{\"resultcode\":\"024\",\"message\":\"Authentication failed\"}")));

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("resultcode=024");
    }

    @Test
    @DisplayName("userinfo response.id 누락 → OAuthProviderException")
    void userInfoMissingId() {
        stubToken("{\"access_token\":\"naver-access-token\"}");
        wireMockServer.stubFor(get(urlEqualTo(USERINFO_PATH))
                .willReturn(json("{\"resultcode\":\"00\",\"response\":{\"email\":\"u@naver.com\"}}")));

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("missing response.id");
    }

    @Test
    @DisplayName("token endpoint 5xx → OAuthProviderException")
    void tokenEndpoint5xx() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void stubToken(String body) {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(json(body)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}

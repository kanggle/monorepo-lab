package com.example.auth.infrastructure.oauth;

import com.example.auth.domain.oauth.OAuthProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
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

@DisplayName("KakaoOAuthClient 단위 테스트")
class KakaoOAuthClientUnitTest {

    private static final String TOKEN_PATH = "/oauth/token";
    private static final String USER_INFO_PATH = "/v2/user/me";
    private static final String REDIRECT_URI = "http://localhost:3000/oauth/callback";

    private WireMockServer wireMockServer;
    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();

        OAuthProperties props = new OAuthProperties();
        OAuthProperties.ProviderProperties kakaoProps = props.getKakao();
        kakaoProps.setClientId("test-kakao-client-id");
        kakaoProps.setClientSecret("test-kakao-secret");
        kakaoProps.setTokenUri(wireMockServer.baseUrl() + TOKEN_PATH);
        kakaoProps.setUserInfoUri(wireMockServer.baseUrl() + USER_INFO_PATH);

        client = new KakaoOAuthClient(props, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("정상 응답 → OAuthUserInfo (id, email, nickname, provider=KAKAO) 반환")
    void exchangeCodeForUserInfo_success_returnsOAuthUserInfo() {
        stubTokenEndpoint("{\"access_token\":\"kakao-access-token\"}");
        stubUserInfoEndpoint("""
                {
                  "id": 12345,
                  "kakao_account": {
                    "email": "alice@kakao.com",
                    "profile": { "nickname": "Alice" }
                  }
                }""");

        OAuthUserInfo info = client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI);

        assertThat(info.providerUserId()).isEqualTo("12345");
        assertThat(info.email()).isEqualTo("alice@kakao.com");
        assertThat(info.name()).isEqualTo("Alice");
        assertThat(info.provider()).isEqualTo(OAuthProvider.KAKAO);
    }

    @Test
    @DisplayName("token 응답에 access_token 누락 → OAuthProviderException")
    void exchangeCodeForUserInfo_missingAccessToken_throwsOAuthProviderException() {
        stubTokenEndpoint("{\"token_type\":\"bearer\"}");

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class)
                .hasMessageContaining("missing access_token");
    }

    @Test
    @DisplayName("token endpoint 5xx → OAuthProviderException")
    void exchangeCodeForUserInfo_tokenEndpoint5xx_throwsOAuthProviderException() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class);
    }

    @Test
    @DisplayName("user info endpoint 5xx → OAuthProviderException")
    void exchangeCodeForUserInfo_userInfoEndpoint5xx_throwsOAuthProviderException() {
        stubTokenEndpoint("{\"access_token\":\"kakao-access-token\"}");
        wireMockServer.stubFor(get(urlEqualTo(USER_INFO_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class);
    }

    @Test
    @DisplayName("네트워크 오류 → OAuthProviderException")
    void exchangeCodeForUserInfo_networkFault_throwsOAuthProviderException() {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code", REDIRECT_URI))
                .isInstanceOf(OAuthProviderException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubTokenEndpoint(String body) {
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubUserInfoEndpoint(String body) {
        wireMockServer.stubFor(get(urlEqualTo(USER_INFO_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}

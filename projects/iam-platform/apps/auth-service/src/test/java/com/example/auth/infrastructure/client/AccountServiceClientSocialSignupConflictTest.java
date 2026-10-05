package com.example.auth.infrastructure.client;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.exception.SocialSignupEmailRegisteredException;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-620 — the social-signup edge discriminates its two 409s by the body's {@code code}
 * (auth-to-account-social.md § Errors): {@code ACCOUNT_ALREADY_EXISTS} (the email has a consumer-pool
 * account) becomes {@link SocialSignupEmailRegisteredException}; {@code TENANT_SUSPENDED} and an
 * unreadable 409 keep the pre-620 answer ({@link AccountServiceUnavailableException}). Scaffolding copied
 * from {@code AccountServiceClientSignupClassificationTest}.
 */
@DisplayName("TASK-BE-620 — social-signup 409 분류")
class AccountServiceClientSocialSignupConflictTest {

    private static final String SOCIAL_SIGNUP_PATH = "/internal/accounts/social-signup";

    private WireMockServer wireMockServer;
    private AccountServiceClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        client = new AccountServiceClient(wireMockServer.baseUrl(), 3000, 5000, tokenProvider);
        // HTTP/1.1 pin — JDK HttpClient's H2C default produces RST_STREAM against WireMock.
        HttpClient jdkHttp11 = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        RestClient http11RestClient = RestClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory(jdkHttp11))
                .build();
        ReflectionTestUtils.setField(client, "cachedRestClient", http11RestClient);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private void stub(int status, String body) {
        wireMockServer.stubFor(post(urlEqualTo(SOCIAL_SIGNUP_PATH)).willReturn(
                aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private void call() {
        client.socialSignup("pool@example.com", "GOOGLE", "google-1", "Pool", "fan-platform");
    }

    @Test
    @DisplayName("409 ACCOUNT_ALREADY_EXISTS → SocialSignupEmailRegisteredException (재시도 없이 한 번만 호출)")
    void accountAlreadyExists_isEmailRegistered() {
        stub(409, "{\"code\":\"ACCOUNT_ALREADY_EXISTS\",\"message\":\"Account already exists\"}");

        assertThatThrownBy(this::call).isInstanceOf(SocialSignupEmailRegisteredException.class);
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(SOCIAL_SIGNUP_PATH)));
    }

    @Test
    @DisplayName("대조군 — 409 TENANT_SUSPENDED 는 이전 그대로 AccountServiceUnavailableException")
    void tenantSuspended_unchanged() {
        stub(409, "{\"code\":\"TENANT_SUSPENDED\",\"message\":\"Tenant is suspended\"}");

        assertThatThrownBy(this::call).isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    @DisplayName("대조군 — 읽을 수 없는 409 본문도 이전 그대로 (판단 불가 → 기존 동작)")
    void unreadableConflict_unchanged() {
        stub(409, "not json");

        assertThatThrownBy(this::call).isInstanceOf(AccountServiceUnavailableException.class);
    }

    // ── TASK-BE-617: the additive `tenantId` field (auth-to-account-social.md) ──

    @Test
    @DisplayName("TASK-BE-617: 201 의 tenantId=consumer-pool 을 읽는다 → poolAccount()")
    void poolResponse_tenantIdRead() {
        stub(201, "{\"accountId\":\"acc-pool\",\"email\":\"pool@example.com\",\"status\":\"ACTIVE\","
                + "\"tenantId\":\"consumer-pool\"}");

        var result = client.socialSignup("pool@example.com", "GOOGLE", "google-1", "Pool", "fan-platform");

        assertThat(result.accountId()).isEqualTo("acc-pool");
        assertThat(result.tenantId()).isEqualTo("consumer-pool");
        assertThat(result.poolAccount()).isTrue();
    }

    @Test
    @DisplayName("TASK-BE-617 대조군: tenantId 가 없는 응답(이전 account-service) → 사이트 계정으로 읽는다")
    void legacyResponse_withoutTenantId_isNotPool() {
        stub(200, "{\"accountId\":\"acc-site\",\"email\":\"pool@example.com\",\"status\":\"ACTIVE\"}");

        var result = client.socialSignup("pool@example.com", "GOOGLE", "google-1", "Pool", "fan-platform");

        assertThat(result.accountId()).isEqualTo("acc-site");
        assertThat(result.tenantId()).isNull();
        assertThat(result.poolAccount()).isFalse();
    }
}

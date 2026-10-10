package com.example.auth.infrastructure.client;

import com.example.auth.application.port.ConsumerPoolSignupPort.Outcome;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-772 S3 — {@link AccountServicePoolSignupClient} (auth-to-account.md § {@code POST
 * /internal/consumer-pool/signups}): the workload Bearer, no tenant header, the answer mapping, and no retry.
 */
@DisplayName("AccountServicePoolSignupClient 단위 테스트 (TASK-MONO-772 S3)")
class AccountServicePoolSignupClientUnitTest {

    private WireMockServer wireMockServer;
    private AccountServicePoolSignupClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AccountServiceClient.BASE_URL_PROPERTY, wireMockServer.baseUrl());
        client = new AccountServicePoolSignupClient(environment, 3000, 5000, tokenProvider);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private void stub(int status, String body) {
        wireMockServer.stubFor(post(urlPathEqualTo(AccountServicePoolSignupClient.PATH)).willReturn(
                aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    @DisplayName("201 → CREATED · Bearer · 본문 = {email, password, displayName} · X-Tenant-Id 없음")
    void created() {
        stub(201, "{\"accountId\":\"acc-1\",\"email\":\"i@example.com\",\"status\":\"ACTIVE\"}");

        assertThat(client.signup("i@example.com", "Password1!", "피초대자")).isEqualTo(Outcome.CREATED);
        wireMockServer.verify(postRequestedFor(urlPathEqualTo(AccountServicePoolSignupClient.PATH))
                .withHeader("Authorization", equalTo("Bearer test-jwt"))
                .withoutHeader("X-Tenant-Id")
                .withRequestBody(equalToJson(
                        "{\"email\":\"i@example.com\",\"password\":\"Password1!\",\"displayName\":\"피초대자\"}")));
    }

    @Test
    @DisplayName("409 ACCOUNT_ALREADY_EXISTS · 409 CONSUMER_POOL_DISABLED · 422 VALIDATION_ERROR · 429 · 503 · 연결 끊김")
    void answers() {
        stub(409, "{\"code\":\"ACCOUNT_ALREADY_EXISTS\"}");
        assertThat(client.signup("i@example.com", "Password1!", null)).isEqualTo(Outcome.ALREADY_EXISTS);
        stub(409, "{\"code\":\"CONSUMER_POOL_DISABLED\"}");
        assertThat(client.signup("i@example.com", "Password1!", null)).isEqualTo(Outcome.NOT_POSSIBLE);
        stub(422, "{\"code\":\"VALIDATION_ERROR\"}");
        assertThat(client.signup("i@example.com", "Password1!", null)).isEqualTo(Outcome.INVALID);
        stub(429, "{\"code\":\"RATE_LIMITED\"}");
        assertThat(client.signup("i@example.com", "Password1!", null)).isEqualTo(Outcome.UNAVAILABLE);
        stub(503, "{\"code\":\"AUTH_SERVICE_UNAVAILABLE\"}");
        assertThat(client.signup("i@example.com", "Password1!", null)).isEqualTo(Outcome.UNAVAILABLE);
        wireMockServer.stubFor(post(urlPathEqualTo(AccountServicePoolSignupClient.PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThat(client.signup("i@example.com", "Password1!", null)).isEqualTo(Outcome.UNAVAILABLE);
        wireMockServer.verify(6, postRequestedFor(urlPathEqualTo(AccountServicePoolSignupClient.PATH)));
    }

    @Test
    @DisplayName("🔴 본문 toString 은 비밀번호 · 주소를 찍지 않는다")
    void body_redacts() {
        String s = new AccountServicePoolSignupClient.SignupBody("i@example.com", "Password1!", "x").toString();
        assertThat(s).doesNotContain("Password1!").doesNotContain("i@example.com");
    }
}

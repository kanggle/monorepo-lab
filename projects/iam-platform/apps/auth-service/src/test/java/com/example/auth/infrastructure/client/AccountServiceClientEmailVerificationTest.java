package com.example.auth.infrastructure.client;

import com.example.auth.application.port.AccountServicePort.EmailVerificationConfirmOutcome;
import com.example.auth.application.port.AccountServicePort.EmailVerificationRequestOutcome;
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
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TASK-MONO-770 — the two server-side calls behind the IdP email-verification pages. Each account-service answer
 * maps to one page outcome by the body's {@code code} (never the status alone); an unreadable answer is the
 * «retry may help» side.
 */
@DisplayName("AccountServiceClient — 인증 메일 요청 · 링크 확인 (TASK-MONO-770)")
class AccountServiceClientEmailVerificationTest {

    private static final String RESEND = "/api/accounts/signup/resend-verification-email";
    private static final String VERIFY = "/api/accounts/signup/verify-email";

    private WireMockServer wireMockServer;
    private AccountServiceClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        client = new AccountServiceClient(wireMockServer.baseUrl(), 3000, 5000,
                mock(IamClientCredentialsTokenProvider.class));
        RestClient http11 = RestClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
                .build();
        ReflectionTestUtils.setField(client, "cachedRestClient", http11);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private void resendAnswers(int status, String code) {
        wireMockServer.stubFor(post(urlEqualTo(RESEND)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(code == null ? "" : "{\"code\":\"" + code + "\",\"message\":\"m\"}")));
    }

    @Test
    @DisplayName("204 → SENT · X-Account-Id / X-Tenant-Id 전달 · Bearer 없음(공개 account-api)")
    void resend_204_sent() {
        resendAnswers(204, null);

        assertThat(client.requestVerificationEmail("acc-770", "consumer-pool"))
                .isEqualTo(EmailVerificationRequestOutcome.SENT);
        wireMockServer.verify(postRequestedFor(urlEqualTo(RESEND))
                .withHeader("X-Account-Id", equalTo("acc-770"))
                .withHeader("X-Tenant-Id", equalTo("consumer-pool"))
                .withHeader("Authorization", absent()));
    }

    @Test
    @DisplayName("코드별 결과: 409 ALREADY_VERIFIED · 429 RATE_LIMITED · 422 UNDELIVERABLE · 404 NOT_APPLICABLE · 503 SEND_FAILED")
    void resend_codes() {
        resendAnswers(409, "EMAIL_ALREADY_VERIFIED");
        assertThat(client.requestVerificationEmail("a", null)).isEqualTo(EmailVerificationRequestOutcome.ALREADY_VERIFIED);
        resendAnswers(429, "RATE_LIMITED");
        assertThat(client.requestVerificationEmail("a", null)).isEqualTo(EmailVerificationRequestOutcome.RATE_LIMITED);
        resendAnswers(422, "VERIFICATION_EMAIL_UNDELIVERABLE");
        assertThat(client.requestVerificationEmail("a", null)).isEqualTo(EmailVerificationRequestOutcome.UNDELIVERABLE);
        resendAnswers(404, "ACCOUNT_NOT_FOUND");
        assertThat(client.requestVerificationEmail("a", null)).isEqualTo(EmailVerificationRequestOutcome.NOT_APPLICABLE);
        resendAnswers(503, "VERIFICATION_EMAIL_SEND_FAILED");
        assertThat(client.requestVerificationEmail("a", null)).isEqualTo(EmailVerificationRequestOutcome.SEND_FAILED);
    }

    @Test
    @DisplayName("판정 불가(빈 본문 · 처음 보는 코드 · 연결 실패) → SEND_FAILED (재시도 쪽)")
    void resend_unjudgeable_sendFailed() {
        resendAnswers(500, null);
        assertThat(client.requestVerificationEmail("a", null)).isEqualTo(EmailVerificationRequestOutcome.SEND_FAILED);
        resendAnswers(400, "SOMETHING_NEW");
        assertThat(client.requestVerificationEmail("a", null)).isEqualTo(EmailVerificationRequestOutcome.SEND_FAILED);

        wireMockServer.stop();
        assertThat(client.requestVerificationEmail("a", null)).isEqualTo(EmailVerificationRequestOutcome.SEND_FAILED);
    }

    @Test
    @DisplayName("링크 확인: 200 VERIFIED(본문 = token) · 400 INVALID_OR_EXPIRED · 409 ALREADY_VERIFIED · 그 밖 UNAVAILABLE")
    void confirm_outcomes() {
        wireMockServer.stubFor(post(urlEqualTo(VERIFY)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"accountId\":\"a\",\"emailVerifiedAt\":\"2026-10-07T00:00:00Z\"}")));
        assertThat(client.confirmEmailVerification("tok-1")).isEqualTo(EmailVerificationConfirmOutcome.VERIFIED);
        wireMockServer.verify(postRequestedFor(urlEqualTo(VERIFY)).withRequestBody(equalToJson("{\"token\":\"tok-1\"}")));

        wireMockServer.stubFor(post(urlEqualTo(VERIFY)).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json").withBody("{\"code\":\"TOKEN_EXPIRED_OR_INVALID\"}")));
        assertThat(client.confirmEmailVerification("tok-1")).isEqualTo(EmailVerificationConfirmOutcome.INVALID_OR_EXPIRED);

        wireMockServer.stubFor(post(urlEqualTo(VERIFY)).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/json").withBody("{\"code\":\"EMAIL_ALREADY_VERIFIED\"}")));
        assertThat(client.confirmEmailVerification("tok-1")).isEqualTo(EmailVerificationConfirmOutcome.ALREADY_VERIFIED);

        wireMockServer.stubFor(post(urlEqualTo(VERIFY)).willReturn(aResponse().withStatus(503)));
        assertThat(client.confirmEmailVerification("tok-1")).isEqualTo(EmailVerificationConfirmOutcome.UNAVAILABLE);
    }
}

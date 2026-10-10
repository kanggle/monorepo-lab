package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.port.OperatorInvitationMailPort;
import com.example.admin.application.port.OperatorInvitationMailPort.DeliveryStatus;
import com.example.admin.application.port.VerifiedEmailMatchPort.MatchResult;
import com.example.admin.application.port.VerifiedEmailMatchPort.Outcome;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-772 S2 — the admin → account adapter of the invitation (admin-to-account.md): the match's
 * refusal → outcome mapping and its fail-closed cases, and the mail's status → delivery mapping (no exception).
 */
@DisplayName("AccountServiceInvitationClient — 인증 이메일 일치 · 초대 메일 (TASK-MONO-772 S2)")
class AccountServiceInvitationClientTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000772";
    private static final String MATCH = "/internal/accounts/" + ACCOUNT + "/verified-email:match";

    private WireMockServer wireMock;
    private AccountServiceInvitationClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        client = new AccountServiceInvitationClient(wireMock.baseUrl(), 3000, 5000, tokenProvider);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    private void stubMatch(int status, String body) {
        wireMock.stubFor(post(urlPathEqualTo(MATCH)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    @DisplayName("200 + emailVerifiedAt → MATCHED · 본문 expectedEmail · Bearer")
    void match_200() {
        stubMatch(200, "{\"accountId\":\"" + ACCOUNT + "\",\"emailVerifiedAt\":\"2026-10-09T10:00:00Z\"}");
        MatchResult r = client.match(ACCOUNT, "person@example.com");
        assertThat(r.outcome()).isEqualTo(Outcome.MATCHED);
        assertThat(r.emailVerifiedAt()).isEqualTo(Instant.parse("2026-10-09T10:00:00Z"));
        wireMock.verify(postRequestedFor(urlPathEqualTo(MATCH))
                .withHeader("Authorization", equalTo("Bearer test-jwt"))
                .withRequestBody(equalToJson("{\"expectedEmail\":\"person@example.com\"}")));
    }

    @Test
    @DisplayName("404 → NOT_ELIGIBLE · 403 ACCOUNT_EMAIL_MISMATCH → EMAIL_MISMATCH · 403 EMAIL_NOT_VERIFIED → NOT_VERIFIED")
    void match_refusals() {
        stubMatch(404, "{\"code\":\"ACCOUNT_NOT_FOUND\"}");
        assertThat(client.match(ACCOUNT, "x@y.example").outcome()).isEqualTo(Outcome.NOT_ELIGIBLE);
        stubMatch(403, "{\"code\":\"ACCOUNT_EMAIL_MISMATCH\"}");
        assertThat(client.match(ACCOUNT, "x@y.example").outcome()).isEqualTo(Outcome.EMAIL_MISMATCH);
        stubMatch(403, "{\"code\":\"EMAIL_NOT_VERIFIED\"}");
        assertThat(client.match(ACCOUNT, "x@y.example").outcome()).isEqualTo(Outcome.NOT_VERIFIED);
    }

    @Test
    @DisplayName("🔴 fail-closed: 200 인데 emailVerifiedAt 없음 · 모르는 403 · 5xx · 연결 끊김 → 예외 (판정 없음)")
    void match_failClosed() {
        stubMatch(200, "{\"accountId\":\"" + ACCOUNT + "\"}");
        assertThatThrownBy(() -> client.match(ACCOUNT, "x@y.example")).isInstanceOf(NonRetryableDownstreamException.class);
        stubMatch(403, "{\"code\":\"SOMETHING_ELSE\"}");
        assertThatThrownBy(() -> client.match(ACCOUNT, "x@y.example")).isInstanceOf(NonRetryableDownstreamException.class);
        stubMatch(503, "{}");
        assertThatThrownBy(() -> client.match(ACCOUNT, "x@y.example"))
                .isInstanceOf(DownstreamFailureException.class).isNotInstanceOf(NonRetryableDownstreamException.class);
        wireMock.stubFor(post(urlPathEqualTo(MATCH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> client.match(ACCOUNT, "x@y.example")).isInstanceOf(DownstreamFailureException.class);
    }

    private static OperatorInvitationMailPort.InvitationMail mail() {
        return new OperatorInvitationMailPort.InvitationMail("person@example.com", "raw-token-xyz", "acme-corp",
                "김관리", Instant.parse("2026-10-17T10:00:00Z"));
    }

    @Test
    @DisplayName("mail 204 → SENT · 본문에 원문 토큰(링크는 account 가 만든다)")
    void mail_sent() {
        wireMock.stubFor(post(urlPathEqualTo("/internal/notifications/operator-invitation"))
                .willReturn(aResponse().withStatus(204)));
        assertThat(client.send(mail())).isEqualTo(DeliveryStatus.SENT);
        wireMock.verify(postRequestedFor(urlPathEqualTo("/internal/notifications/operator-invitation"))
                .withRequestBody(equalToJson("""
                        {"to":"person@example.com","token":"raw-token-xyz","tenantId":"acme-corp",
                         "inviterDisplayName":"김관리","expiresAt":"2026-10-17T10:00:00Z"}""")));
    }

    @Test
    @DisplayName("mail 422 UNDELIVERABLE → FAILED_PERMANENT · 503 · 다른 4xx · 끊김 → FAILED_TRANSIENT · 재시도 없음(1회)")
    void mail_failures() {
        String path = "/internal/notifications/operator-invitation";
        wireMock.stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withStatus(422)
                .withHeader("Content-Type", "application/json").withBody("{\"code\":\"INVITATION_EMAIL_UNDELIVERABLE\"}")));
        assertThat(client.send(mail())).isEqualTo(DeliveryStatus.FAILED_PERMANENT);

        wireMock.resetAll();
        wireMock.stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withStatus(503)
                .withHeader("Content-Type", "application/json").withBody("{\"code\":\"INVITATION_EMAIL_SEND_FAILED\"}")));
        assertThat(client.send(mail())).isEqualTo(DeliveryStatus.FAILED_TRANSIENT);
        wireMock.verify(1, postRequestedFor(urlPathEqualTo(path)));

        wireMock.stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json").withBody("{\"code\":\"VALIDATION_ERROR\"}")));
        assertThat(client.send(mail())).isEqualTo(DeliveryStatus.FAILED_TRANSIENT);

        wireMock.stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThat(client.send(mail())).isEqualTo(DeliveryStatus.FAILED_TRANSIENT);
    }

    @Test
    @DisplayName("🔴 본문 toString 은 토큰 · 주소를 싣지 않는다 — 메시지 컨버터가 DEBUG 로 찍는 것이 이 문자열이다(S2 CI 실측)")
    void bodies_redactOnToString() {
        String mailBody = new AccountServiceInvitationClient.MailRequest(
                "person@example.com", "raw-token-xyz", "acme-corp", "김관리", "2026-10-17T10:00:00Z").toString();
        assertThat(mailBody).doesNotContain("raw-token-xyz").doesNotContain("person@example.com").contains("acme-corp");

        String matchBody = new AccountServiceInvitationClient.MatchRequest("person@example.com").toString();
        assertThat(matchBody).doesNotContain("person@example.com");
    }

    @Test
    @DisplayName("inviterDisplayName 이 없으면 본문에서 빠진다(맵 시절과 같은 모양)")
    void mail_omitsAbsentInviterName() {
        wireMock.stubFor(post(urlPathEqualTo("/internal/notifications/operator-invitation"))
                .willReturn(aResponse().withStatus(204)));
        client.send(new OperatorInvitationMailPort.InvitationMail("person@example.com", "raw-token-xyz", "acme-corp",
                " ", Instant.parse("2026-10-17T10:00:00Z")));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/internal/notifications/operator-invitation"))
                .withRequestBody(equalToJson("""
                        {"to":"person@example.com","token":"raw-token-xyz","tenantId":"acme-corp",
                         "expiresAt":"2026-10-17T10:00:00Z"}""")));
    }
}

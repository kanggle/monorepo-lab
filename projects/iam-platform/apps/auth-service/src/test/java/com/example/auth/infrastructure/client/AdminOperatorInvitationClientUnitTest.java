package com.example.auth.infrastructure.client;

import com.example.auth.application.port.OperatorInvitationAcceptancePort.AcceptOutcome;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.AcceptResult;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.PreviewOutcome;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.PreviewResult;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.Map;

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
 * TASK-MONO-772 S3 — {@link AdminOperatorInvitationClient} (auth-to-admin.md § preview · § accept): the token in a
 * POST body (never a URL), the workload Bearer, every contract refusal as a value, «no answer» as UNAVAILABLE, and
 * 🔴 the accept is never retried.
 */
@DisplayName("AdminOperatorInvitationClient 단위 테스트 (TASK-MONO-772 S3)")
class AdminOperatorInvitationClientUnitTest {

    private static final String TOKEN = "raw-token-772-s3";
    private static final String ACCOUNT = "0199de70-0000-7000-8000-0000000acc01";

    private WireMockServer wireMockServer;
    private AdminOperatorInvitationClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AdminAssignmentClient.BASE_URL_PROPERTY, wireMockServer.baseUrl());
        client = new AdminOperatorInvitationClient(environment, 3000, 5000, tokenProvider);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private void stub(String path, int status, String body) {
        wireMockServer.stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    @DisplayName("preview 200 → FOUND(회사 · 마스킹 · 역할 · 만료) · 토큰은 본문 · Bearer")
    void preview_found() {
        stub(AdminOperatorInvitationClient.PREVIEW_PATH, 200, """
                {"tenantId":"acme-corp","tenantDisplayName":null,"maskedEmail":"p*****@example.com",
                 "roles":["SUPPORT_LOCK"],"status":"PENDING","expired":false,"expiresAt":"2026-10-17T10:00:00Z"}""");

        PreviewResult result = client.preview(TOKEN);

        assertThat(result.outcome()).isEqualTo(PreviewOutcome.FOUND);
        assertThat(result.preview().companyName()).as("display name null → the tenant id").isEqualTo("acme-corp");
        assertThat(result.preview().maskedEmail()).isEqualTo("p*****@example.com");
        assertThat(result.preview().roles()).containsExactly("SUPPORT_LOCK");
        assertThat(result.preview().expiresAt()).isEqualTo(Instant.parse("2026-10-17T10:00:00Z"));
        wireMockServer.verify(postRequestedFor(urlPathEqualTo(AdminOperatorInvitationClient.PREVIEW_PATH))
                .withHeader("Authorization", equalTo("Bearer test-jwt"))
                .withRequestBody(equalToJson("{\"token\":\"" + TOKEN + "\"}")));
    }

    @Test
    @DisplayName("preview 404 → NOT_FOUND · 503 / 연결 끊김 → UNAVAILABLE")
    void preview_notFound_andUnavailable() {
        stub(AdminOperatorInvitationClient.PREVIEW_PATH, 404, "{\"code\":\"OPERATOR_INVITATION_NOT_FOUND\"}");
        assertThat(client.preview(TOKEN).outcome()).isEqualTo(PreviewOutcome.NOT_FOUND);

        stub(AdminOperatorInvitationClient.PREVIEW_PATH, 503, "{\"code\":\"DOWNSTREAM_ERROR\"}");
        assertThat(client.preview(TOKEN).outcome()).isEqualTo(PreviewOutcome.UNAVAILABLE);

        wireMockServer.stubFor(post(urlPathEqualTo(AdminOperatorInvitationClient.PREVIEW_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThat(client.preview(TOKEN).outcome()).isEqualTo(PreviewOutcome.UNAVAILABLE);
    }

    @Test
    @DisplayName("accept 200 → ACCEPTED · alreadyAccepted=true → ALREADY_ACCEPTED · 본문 = {token, accountId}")
    void accept_ok() {
        stub(AdminOperatorInvitationClient.ACCEPT_PATH, 200,
                "{\"operatorId\":\"op-1\",\"tenantId\":\"acme-corp\",\"roles\":[\"SUPPORT_LOCK\"],\"alreadyAccepted\":false}");
        AcceptResult first = client.accept(TOKEN, ACCOUNT);
        assertThat(first.outcome()).isEqualTo(AcceptOutcome.ACCEPTED);
        assertThat(first.tenantId()).isEqualTo("acme-corp");
        wireMockServer.verify(postRequestedFor(urlPathEqualTo(AdminOperatorInvitationClient.ACCEPT_PATH))
                .withHeader("Authorization", equalTo("Bearer test-jwt"))
                .withRequestBody(equalToJson("{\"token\":\"" + TOKEN + "\",\"accountId\":\"" + ACCOUNT + "\"}")));

        stub(AdminOperatorInvitationClient.ACCEPT_PATH, 200,
                "{\"operatorId\":\"op-1\",\"tenantId\":\"acme-corp\",\"roles\":[],\"alreadyAccepted\":true}");
        assertThat(client.accept(TOKEN, ACCOUNT).outcome()).isEqualTo(AcceptOutcome.ALREADY_ACCEPTED);
    }

    @Test
    @DisplayName("계약의 거절 코드 9개 → 각 값 (예외 아님)")
    void accept_refusalsAreValues() {
        Map<String, Integer> statusOf = Map.of(
                "EMAIL_NOT_VERIFIED", 403, "OPERATOR_INVITATION_EMAIL_MISMATCH", 403,
                "OPERATOR_INVITATION_ACCOUNT_NOT_ELIGIBLE", 403, "OPERATOR_INVITATION_NOT_FOUND", 404,
                "OPERATOR_INVITATION_ALREADY_USED", 409, "OPERATOR_ALREADY_PROVISIONED", 409,
                "OPERATOR_EMAIL_CONFLICT", 409, "OPERATOR_INVITATION_INVALIDATED", 409,
                "OPERATOR_INVITATION_EXPIRED", 410);
        assertThat(statusOf.keySet()).isEqualTo(AdminOperatorInvitationClient.ACCEPT_CODES.keySet());
        statusOf.forEach((code, status) -> {
            stub(AdminOperatorInvitationClient.ACCEPT_PATH, status, "{\"code\":\"" + code + "\",\"message\":\"x\"}");
            assertThat(client.accept(TOKEN, ACCOUNT).outcome()).as(code)
                    .isEqualTo(AdminOperatorInvitationClient.ACCEPT_CODES.get(code));
        });
    }

    @Test
    @DisplayName("🔴 503 → UNAVAILABLE · 재시도 없음(요청 1회) · 모르는 4xx · 읽을 수 없는 200 → UNAVAILABLE")
    void accept_noAnswer_notRetried() {
        stub(AdminOperatorInvitationClient.ACCEPT_PATH, 503, "{\"code\":\"DOWNSTREAM_ERROR\"}");
        assertThat(client.accept(TOKEN, ACCOUNT).outcome()).isEqualTo(AcceptOutcome.UNAVAILABLE);
        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo(AdminOperatorInvitationClient.ACCEPT_PATH)));

        stub(AdminOperatorInvitationClient.ACCEPT_PATH, 400, "{\"code\":\"SOMETHING_NEW\"}");
        assertThat(client.accept(TOKEN, ACCOUNT).outcome()).isEqualTo(AcceptOutcome.UNAVAILABLE);

        stub(AdminOperatorInvitationClient.ACCEPT_PATH, 200, "{\"operatorId\":\"op-1\"}");
        assertThat(client.accept(TOKEN, ACCOUNT).outcome()).isEqualTo(AcceptOutcome.UNAVAILABLE);
    }

    @Test
    @DisplayName("🔴 R4: 요청 레코드 toString 은 토큰을 찍지 않는다")
    void bodies_redactTheToken() {
        assertThat(new AdminOperatorInvitationClient.PreviewRequest(TOKEN).toString()).doesNotContain(TOKEN);
        assertThat(new AdminOperatorInvitationClient.AcceptRequest(TOKEN, ACCOUNT).toString())
                .doesNotContain(TOKEN).contains(ACCOUNT);
    }
}

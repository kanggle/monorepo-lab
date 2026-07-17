package com.example.account.infrastructure.client;

import com.example.account.application.port.AuthServicePort;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuthServiceClient 단위 테스트")
class AuthServiceClientUnitTest {

    private static final String CREDENTIALS_PATH = "/internal/auth/credentials";
    private static final String BACKFILL_PATH = "/internal/auth/credentials/identity-backfill";
    private static final String TOKEN_PATH = "/oauth2/token";

    private WireMockServer wireMockServer;
    private AuthServiceClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        // TASK-BE-487: the client now mints a GAP client_credentials token before each /internal/auth/**
        // call and presents it as Authorization: Bearer. Point the provider at this same WireMock and
        // stub the token endpoint so the outbound credential calls carry a real bearer.
        wireMockServer.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-cc-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        IamClientCredentialsTokenProvider tokenProvider = new IamClientCredentialsTokenProvider(
                wireMockServer.baseUrl() + TOKEN_PATH, "account-service-client", "secret");
        // AuthServiceClient now enforces HTTP/1.1 internally, so no workaround needed here.
        client = new AuthServiceClient(wireMockServer.baseUrl(), 3000, 5000, tokenProvider);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("createCredential — 200 응답 → 예외 없이 완료")
    void createCredential_success_noException() {
        wireMockServer.stubFor(post(urlEqualTo(CREDENTIALS_PATH))
                .willReturn(aResponse().withStatus(200)));

        assertThatNoException().isThrownBy(() ->
                client.createCredential("acc-1", "user@example.com", "pass123", "fan-platform", null));
    }

    @Test
    @DisplayName("createCredential — TASK-BE-487: Authorization: Bearer <GAP client_credentials JWT> 전송")
    void createCredential_sendsBearerJwt() {
        wireMockServer.stubFor(post(urlEqualTo(CREDENTIALS_PATH))
                .willReturn(aResponse().withStatus(201)));

        client.createCredential("acc-b", "user@example.com", "pass123", "fan-platform", null);

        wireMockServer.verify(postRequestedFor(urlEqualTo(CREDENTIALS_PATH))
                .withHeader("Authorization", equalTo("Bearer test-cc-token")));
        // The token was minted from the GAP token endpoint with the account-service-client credentials,
        // requesting the internal.invoke scope the /internal/** receiver now requires (TASK-BE-515).
        wireMockServer.verify(postRequestedFor(urlEqualTo(TOKEN_PATH))
                .withRequestBody(matching(".*grant_type=client_credentials.*"))
                .withRequestBody(matching(".*scope=internal\\.invoke.*")));
    }

    @Test
    @DisplayName("createCredential — 요청 바디에 tenantId 포함, accountType 미포함 (TASK-MONO-263)")
    void createCredential_bodyCarriesTenant_noAccountType() {
        wireMockServer.stubFor(post(urlEqualTo(CREDENTIALS_PATH))
                .willReturn(aResponse().withStatus(201)));

        client.createCredential("acc-op", "operator@example.com", "pass123", "acme-corp", "idy-op");

        wireMockServer.verify(postRequestedFor(urlEqualTo(CREDENTIALS_PATH))
                .withRequestBody(matchingJsonPath("$[?(@.tenantId == 'acme-corp')]"))
                .withRequestBody(matchingJsonPath("$[?(@.accountId == 'acc-op')]"))
                // TASK-BE-384 (ADR-036 M2): the born-unified identityId is propagated in the body.
                .withRequestBody(matchingJsonPath("$[?(@.identityId == 'idy-op')]"))
                // TASK-MONO-263: accountType is never sent.
                .withRequestBody(matchingJsonPath("$[?(!@.accountType)]")));
    }

    @Test
    @DisplayName("createCredential — 409 응답 → CredentialAlreadyExistsConflict")
    void createCredential_conflict409_throwsCredentialAlreadyExistsConflict() {
        wireMockServer.stubFor(post(urlEqualTo(CREDENTIALS_PATH))
                .willReturn(aResponse().withStatus(409)));

        assertThatThrownBy(() -> client.createCredential("acc-2", "dup@example.com", "pass123", "fan-platform", null))
                .isInstanceOf(AuthServicePort.CredentialAlreadyExistsConflict.class);
    }

    @Test
    @DisplayName("createCredential — 기타 4xx 응답 → AuthServiceUnavailable")
    void createCredential_otherClientError_throwsAuthServiceUnavailable() {
        wireMockServer.stubFor(post(urlEqualTo(CREDENTIALS_PATH))
                .willReturn(aResponse().withStatus(422)));

        assertThatThrownBy(() -> client.createCredential("acc-3", "user@example.com", "pass123", "fan-platform", null))
                .isInstanceOf(AuthServicePort.AuthServiceUnavailable.class);
    }

    @Test
    @DisplayName("createCredential — 네트워크 오류 (연결 끊김) → AuthServiceUnavailable (fail-closed, retry 후)")
    void createCredential_networkFault_throwsAuthServiceUnavailable() {
        wireMockServer.stubFor(post(urlEqualTo(CREDENTIALS_PATH))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> client.createCredential("acc-err", "user@example.com", "pass123", "fan-platform", null))
                .isInstanceOf(AuthServicePort.AuthServiceUnavailable.class);
    }

    // ── TASK-BE-386 (ADR-036 M4): credential identity backfill ───────────────────

    @Test
    @DisplayName("backfillCredentialIdentities — items 바디 전송 + 응답 updated 반환")
    void backfill_sendsItemsBody_returnsUpdated() {
        wireMockServer.stubFor(post(urlEqualTo(BACKFILL_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"requested\":2,\"updated\":1}")));

        int updated = client.backfillCredentialIdentities(List.of(
                new AuthServicePort.CredentialIdentityBinding("acc-1", "idy-1"),
                new AuthServicePort.CredentialIdentityBinding("acc-2", "idy-2")));

        assertThat(updated).isEqualTo(1);
        wireMockServer.verify(postRequestedFor(urlEqualTo(BACKFILL_PATH))
                .withRequestBody(matchingJsonPath("$.items[?(@.accountId == 'acc-1')]"))
                .withRequestBody(matchingJsonPath("$.items[?(@.identityId == 'idy-1')]"))
                .withRequestBody(matchingJsonPath("$.items[?(@.accountId == 'acc-2')]")));
    }

    @Test
    @DisplayName("backfillCredentialIdentities — 빈 리스트 → HTTP 호출 없이 0 반환")
    void backfill_emptyList_noHttpCall() {
        assertThat(client.backfillCredentialIdentities(List.of())).isZero();
        wireMockServer.verify(0, postRequestedFor(urlEqualTo(BACKFILL_PATH)));
    }

    @Test
    @DisplayName("backfillCredentialIdentities — 4xx → AuthServiceUnavailable")
    void backfill_clientError_throwsAuthServiceUnavailable() {
        wireMockServer.stubFor(post(urlEqualTo(BACKFILL_PATH))
                .willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> client.backfillCredentialIdentities(
                List.of(new AuthServicePort.CredentialIdentityBinding("acc-x", "idy-x"))))
                .isInstanceOf(AuthServicePort.AuthServiceUnavailable.class);
    }
}

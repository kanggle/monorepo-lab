package com.example.auth.infrastructure.client;

import com.example.auth.application.exception.OperatorEligibilityUnavailableException;
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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-772 S4 — {@link AdminConsoleEligibilityClient} (auth-to-admin.md § GET
 * /internal/operators/console-eligibility). The defining property is <b>fail-CLOSED</b>: a boolean only on
 * {@code {"eligible": bool}}; every other outcome (4xx, 5xx, network fault, a 200 without the field) throws
 * {@link OperatorEligibilityUnavailableException} — which the issuer turns into the distinct
 * {@code operator_eligibility_unavailable} refusal, never into «no facet».
 */
@DisplayName("AdminConsoleEligibilityClient 단위 테스트 (TASK-MONO-772 S4, fail-closed)")
class AdminConsoleEligibilityClientUnitTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000772";

    private WireMockServer wireMockServer;
    private AdminConsoleEligibilityClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AdminAssignmentClient.BASE_URL_PROPERTY, wireMockServer.baseUrl());
        client = new AdminConsoleEligibilityClient(environment, 3000, 5000, tokenProvider);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private void stub(int status, String body) {
        wireMockServer.stubFor(get(urlPathEqualTo(AdminConsoleEligibilityClient.PATH))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    @DisplayName("eligible=true → true · accountId 쿼리 · 워크로드 Bearer")
    void eligibleTrue() {
        stub(200, "{\"eligible\":true}");

        assertThat(client.isConsoleEligible(ACCOUNT)).isTrue();
        wireMockServer.verify(getRequestedFor(urlPathEqualTo(AdminConsoleEligibilityClient.PATH))
                .withQueryParam("accountId", equalTo(ACCOUNT))
                .withHeader("Authorization", equalTo("Bearer test-jwt")));
    }

    @Test
    @DisplayName("eligible=false → false («아니오» 도 답이다 — 예외 아님)")
    void eligibleFalse() {
        stub(200, "{\"eligible\":false}");

        assertThat(client.isConsoleEligible(ACCOUNT)).isFalse();
    }

    @Test
    @DisplayName("eligible 없는 200 → 판정 불가 (fail-closed)")
    void missingField_unavailable() {
        stub(200, "{\"operatorFaceted\":true}");

        assertThatThrownBy(() -> client.isConsoleEligible(ACCOUNT))
                .isInstanceOf(OperatorEligibilityUnavailableException.class);
    }

    @Test
    @DisplayName("문자열 \"true\" 는 boolean 이 아니다 → 판정 불가")
    void nonBooleanField_unavailable() {
        stub(200, "{\"eligible\":\"true\"}");

        assertThatThrownBy(() -> client.isConsoleEligible(ACCOUNT))
                .isInstanceOf(OperatorEligibilityUnavailableException.class);
    }

    @Test
    @DisplayName("4xx (401 · 400) → 판정 불가")
    void clientError_unavailable() {
        stub(401, "{\"code\":\"UNAUTHORIZED\"}");

        assertThatThrownBy(() -> client.isConsoleEligible(ACCOUNT))
                .isInstanceOf(OperatorEligibilityUnavailableException.class);
    }

    @Test
    @DisplayName("5xx → 판정 불가 (재시도 뒤에도)")
    void serverError_unavailable() {
        stub(503, "{}");

        assertThatThrownBy(() -> client.isConsoleEligible(ACCOUNT))
                .isInstanceOf(OperatorEligibilityUnavailableException.class);
    }

    @Test
    @DisplayName("연결 끊김 → 판정 불가")
    void networkFault_unavailable() {
        wireMockServer.stubFor(get(urlPathEqualTo(AdminConsoleEligibilityClient.PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.isConsoleEligible(ACCOUNT))
                .isInstanceOf(OperatorEligibilityUnavailableException.class);
    }
}

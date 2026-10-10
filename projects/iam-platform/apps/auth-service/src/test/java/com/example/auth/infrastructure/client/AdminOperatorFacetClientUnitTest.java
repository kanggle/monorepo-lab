package com.example.auth.infrastructure.client;

import com.example.auth.application.exception.OperatorFacetUnavailableException;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
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
 * TASK-MONO-772 S6 — {@link AdminOperatorFacetClient} asks the identity axis only ({@code axes=IDENTITY},
 * auth-to-admin.md § facet · auth-internal.md 판정 6), and stays fail-CLOSED (TASK-BE-618).
 */
@DisplayName("AdminOperatorFacetClient 단위 테스트 (TASK-MONO-772 S6 — axes=IDENTITY, fail-closed)")
class AdminOperatorFacetClientUnitTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000776";
    private static final String IDENTITY = "0199de71-0000-7000-8000-000000000776";

    private WireMockServer wireMockServer;
    private AdminOperatorFacetClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        MockEnvironment environment = new MockEnvironment()
                .withProperty(AdminAssignmentClient.BASE_URL_PROPERTY, wireMockServer.baseUrl());
        client = new AdminOperatorFacetClient(environment, 3000, 5000, tokenProvider);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private void stub(int status, String body) {
        wireMockServer.stubFor(get(urlPathEqualTo(AdminOperatorFacetClient.PATH))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    @DisplayName("🔴 772 S6: 질의에 axes=IDENTITY · accountId · identityId · 워크로드 Bearer")
    void asksIdentityAxisOnly() {
        stub(200, "{\"operatorFaceted\":false}");

        assertThat(client.isOperatorFaceted(ACCOUNT, IDENTITY)).isFalse();
        wireMockServer.verify(getRequestedFor(urlPathEqualTo(AdminOperatorFacetClient.PATH))
                .withQueryParam("axes", equalTo("IDENTITY"))
                .withQueryParam("accountId", equalTo(ACCOUNT))
                .withQueryParam("identityId", equalTo(IDENTITY))
                .withHeader("Authorization", equalTo("Bearer test-jwt")));
    }

    @Test
    @DisplayName("신원 없음 → identityId 파라미터 없음 · axes=IDENTITY 는 그대로")
    void noIdentity_parameterOmitted() {
        stub(200, "{\"operatorFaceted\":false}");

        assertThat(client.isOperatorFaceted(ACCOUNT, null)).isFalse();
        wireMockServer.verify(getRequestedFor(urlPathEqualTo(AdminOperatorFacetClient.PATH))
                .withQueryParam("axes", equalTo("IDENTITY"))
                .withQueryParam("identityId", absent()));
    }

    @Test
    @DisplayName("operatorFaceted=true → true (신원 연결 운영자는 계속 건너뛴다)")
    void facetedTrue() {
        stub(200, "{\"operatorFaceted\":true}");

        assertThat(client.isOperatorFaceted(ACCOUNT, IDENTITY)).isTrue();
    }

    @Test
    @DisplayName("400(admin 이 axes 를 거절) · 필드 없는 200 → 판정 불가 (fail-closed — 옮기지 않는다)")
    void refusalOrMalformed_unavailable() {
        stub(400, "{\"code\":\"VALIDATION_ERROR\"}");
        assertThatThrownBy(() -> client.isOperatorFaceted(ACCOUNT, IDENTITY))
                .isInstanceOf(OperatorFacetUnavailableException.class);

        wireMockServer.resetAll();
        stub(200, "{\"eligible\":true}");
        assertThatThrownBy(() -> client.isOperatorFaceted(ACCOUNT, IDENTITY))
                .isInstanceOf(OperatorFacetUnavailableException.class);
    }
}

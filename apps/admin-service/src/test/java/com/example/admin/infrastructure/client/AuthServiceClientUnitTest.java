package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuthServiceClient 단위 테스트")
class AuthServiceClientUnitTest {

    private static final String FORCE_LOGOUT_PATH = "/internal/auth/accounts/acc-1/force-logout";

    private WireMockServer wireMockServer;
    private AuthServiceClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        // AuthServiceClient constructor explicitly sets HTTP/1.1 — no H2C fix needed.
        client = new AuthServiceClient(wireMockServer.baseUrl(), 3000, 5000, "");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("forceLogout — 200 응답 → ForceLogoutResponse(accountId, revokedTokenCount) 반환")
    void forceLogout_200_returnsResponse() {
        wireMockServer.stubFor(post(urlPathMatching("/internal/auth/accounts/.*/force-logout"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acc-1\",\"revokedTokenCount\":3,\"revokedAt\":null}")));

        AuthServiceClient.ForceLogoutResponse response =
                client.forceLogout("acc-1", "op-1", "ADMIN_LOCK", "idemp-key-1");

        assertThat(response.accountId()).isEqualTo("acc-1");
        assertThat(response.revokedTokenCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("forceLogout — 4xx 응답 → NonRetryableDownstreamException")
    void forceLogout_4xx_throwsNonRetryableDownstreamException() {
        wireMockServer.stubFor(post(urlPathMatching("/internal/auth/accounts/.*/force-logout"))
                .willReturn(aResponse().withStatus(422)));

        assertThatThrownBy(() -> client.forceLogout("acc-1", "op-1", "reason", "idemp-1"))
                .isInstanceOf(NonRetryableDownstreamException.class);
    }

    @Test
    @DisplayName("forceLogout — 5xx 응답 → DownstreamFailureException")
    void forceLogout_5xx_throwsDownstreamFailureException() {
        wireMockServer.stubFor(post(urlPathMatching("/internal/auth/accounts/.*/force-logout"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.forceLogout("acc-1", "op-1", "reason", "idemp-1"))
                .isInstanceOf(DownstreamFailureException.class)
                .isNotInstanceOf(NonRetryableDownstreamException.class);
    }

    @Test
    @DisplayName("forceLogout — 네트워크 오류 → DownstreamFailureException")
    void forceLogout_networkFault_throwsDownstreamFailureException() {
        wireMockServer.stubFor(post(urlPathMatching("/internal/auth/accounts/.*/force-logout"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> client.forceLogout("acc-1", "op-1", "reason", "idemp-1"))
                .isInstanceOf(DownstreamFailureException.class);
    }
}

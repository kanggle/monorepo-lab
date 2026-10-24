package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-811 — the two new downstream reads against a WireMock server: the request each one sends
 * (path, {@code tenantId} / {@code from} / {@code to} as ISO instants, Bearer), the parsed body, and the
 * «never an empty answer» failure mapping (4xx → NonRetryable, 5xx / fault / empty body → DownstreamFailure).
 */
@DisplayName("Metrics downstream clients — security-service · account-service (TASK-MONO-811)")
class MetricsDownstreamClientsTest {

    private static final Instant FROM = Instant.parse("2026-10-18T15:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-23T05:05:00Z");

    private WireMockServer wireMock;
    private SecurityServiceClient security;
    private AccountServiceClient accounts;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        IamClientCredentialsTokenProvider tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
        security = new SecurityServiceClient(wireMock.baseUrl(), 3000, 5000, tokenProvider);
        accounts = new AccountServiceClient(wireMock.baseUrl(), 3000, 5000, tokenProvider);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("security: GET /internal/security/metrics/daily?tenantId&from&to with Bearer → days parsed")
    void securityOk() {
        wireMock.stubFor(get(urlPathEqualTo("/internal/security/metrics/daily"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"tenantId\":\"fan-platform\",\"from\":\"2026-10-18T15:00:00Z\","
                                + "\"to\":\"2026-10-23T05:05:00Z\",\"days\":[{\"date\":\"2026-10-19\","
                                + "\"loginSuccess\":3,\"loginFailure\":1,\"accountLocked\":0}]}")));

        SecurityServiceClient.SecurityMetricsDaily r = security.loginMetricsDaily("fan-platform", FROM, TO);

        assertThat(r.days()).containsExactly(new SecurityServiceClient.SecurityMetricsDaily.Day("2026-10-19", 3, 1, 0));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/internal/security/metrics/daily"))
                .withQueryParam("tenantId", equalTo("fan-platform"))
                .withQueryParam("from", equalTo("2026-10-18T15:00:00Z"))
                .withQueryParam("to", equalTo("2026-10-23T05:05:00Z"))
                .withHeader("Authorization", equalTo("Bearer test-jwt")));
    }

    @Test
    @DisplayName("security: tenantId=* reaches the server as '*'")
    void securityPlatformScope() {
        wireMock.stubFor(get(urlPathEqualTo("/internal/security/metrics/daily"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"tenantId\":\"*\",\"days\":[]}")));

        security.loginMetricsDaily("*", FROM, TO);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/internal/security/metrics/daily"))
                .withQueryParam("tenantId", equalTo("*")));
    }

    @Test
    @DisplayName("security: 4xx → NonRetryable; 5xx → DownstreamFailure; fault → DownstreamFailure; no days → DownstreamFailure")
    void securityFailures() {
        wireMock.stubFor(get(urlPathEqualTo("/internal/security/metrics/daily")).willReturn(aResponse().withStatus(403)));
        assertThatThrownBy(() -> security.loginMetricsDaily("t", FROM, TO))
                .isInstanceOf(NonRetryableDownstreamException.class);

        wireMock.resetAll();
        wireMock.stubFor(get(urlPathEqualTo("/internal/security/metrics/daily")).willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> security.loginMetricsDaily("t", FROM, TO))
                .isInstanceOf(DownstreamFailureException.class)
                .isNotInstanceOf(NonRetryableDownstreamException.class);

        wireMock.resetAll();
        wireMock.stubFor(get(urlPathEqualTo("/internal/security/metrics/daily"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));
        assertThatThrownBy(() -> security.loginMetricsDaily("t", FROM, TO))
                .isInstanceOf(DownstreamFailureException.class);

        wireMock.resetAll();
        wireMock.stubFor(get(urlPathEqualTo("/internal/security/metrics/daily"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"tenantId\":\"t\"}")));
        assertThatThrownBy(() -> security.loginMetricsDaily("t", FROM, TO))
                .isInstanceOf(DownstreamFailureException.class);
    }

    @Test
    @DisplayName("account: GET /internal/account-metrics/daily?tenantId&from&to with Bearer → days parsed")
    void accountOk() {
        wireMock.stubFor(get(urlPathEqualTo("/internal/account-metrics/daily"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"tenantId\":\"fan-platform\",\"from\":\"2026-10-18T15:00:00Z\","
                                + "\"to\":\"2026-10-23T05:05:00Z\",\"days\":[{\"date\":\"2026-10-20\",\"newAccount\":4}]}")));

        AccountServiceClient.AccountMetricsDaily r = accounts.newAccountMetricsDaily("fan-platform", FROM, TO);

        assertThat(r.days()).containsExactly(new AccountServiceClient.AccountMetricsDaily.Day("2026-10-20", 4));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/internal/account-metrics/daily"))
                .withQueryParam("tenantId", equalTo("fan-platform"))
                .withQueryParam("from", equalTo("2026-10-18T15:00:00Z"))
                .withQueryParam("to", equalTo("2026-10-23T05:05:00Z"))
                .withHeader("Authorization", equalTo("Bearer test-jwt")));
    }

    @Test
    @DisplayName("account: 4xx → NonRetryable; 5xx → DownstreamFailure; no days → DownstreamFailure")
    void accountFailures() {
        wireMock.stubFor(get(urlPathEqualTo("/internal/account-metrics/daily")).willReturn(aResponse().withStatus(401)));
        assertThatThrownBy(() -> accounts.newAccountMetricsDaily("t", FROM, TO))
                .isInstanceOf(NonRetryableDownstreamException.class);

        wireMock.resetAll();
        wireMock.stubFor(get(urlPathEqualTo("/internal/account-metrics/daily")).willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> accounts.newAccountMetricsDaily("t", FROM, TO))
                .isInstanceOf(DownstreamFailureException.class)
                .isNotInstanceOf(NonRetryableDownstreamException.class);

        wireMock.resetAll();
        wireMock.stubFor(get(urlPathEqualTo("/internal/account-metrics/daily"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{}")));
        assertThatThrownBy(() -> accounts.newAccountMetricsDaily("t", FROM, TO))
                .isInstanceOf(DownstreamFailureException.class);
    }
}

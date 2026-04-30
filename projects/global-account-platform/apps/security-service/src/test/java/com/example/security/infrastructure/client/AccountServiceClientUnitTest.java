package com.example.security.infrastructure.client;

import com.example.security.domain.Tenants;
import com.example.security.domain.detection.AccountLockClient.LockResult;
import com.example.security.domain.detection.AccountLockClient.Status;
import com.example.security.domain.detection.RiskLevel;
import com.example.security.domain.suspicious.SuspiciousEvent;
import com.example.security.infrastructure.config.DetectionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccountServiceClient 단위 테스트")
class AccountServiceClientUnitTest {

    private WireMockServer wireMock;
    private AccountServiceClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        DetectionProperties props = new DetectionProperties();
        DetectionProperties.AutoLock autoLock = new DetectionProperties.AutoLock();
        autoLock.setAccountServiceBaseUrl(wireMock.baseUrl());
        autoLock.setMaxAttempts(1);
        autoLock.setInitialBackoffMs(1L);
        autoLock.setConnectTimeoutMs(3000);
        autoLock.setReadTimeoutMs(5000);
        props.setAutoLock(autoLock);

        client = new AccountServiceClient(props, new ObjectMapper(), new SimpleMeterRegistry(), "");
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("200 응답 + previousStatus 활성 → SUCCESS")
    void lock_200_previousStatusActive_returnsSuccess() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acc-1\",\"previousStatus\":\"ACTIVE\"," +
                                "\"currentStatus\":\"LOCKED\",\"lockedAt\":\"2026-01-01T00:00:00Z\"}")));

        LockResult result = client.lock(buildEvent("acc-1"));

        assertThat(result.status()).isEqualTo(Status.SUCCESS);
        assertThat(result.httpStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 응답 + previousStatus LOCKED → ALREADY_LOCKED (멱등 응답)")
    void lock_200_previousStatusLocked_returnsAlreadyLocked() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acc-1\",\"previousStatus\":\"LOCKED\"," +
                                "\"currentStatus\":\"LOCKED\",\"lockedAt\":\"2026-01-01T00:00:00Z\"}")));

        LockResult result = client.lock(buildEvent("acc-1"));

        assertThat(result.status()).isEqualTo(Status.ALREADY_LOCKED);
    }

    @Test
    @DisplayName("409 응답 → INVALID_TRANSITION (재시도 금지)")
    void lock_409_returnsInvalidTransition() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"INVALID_STATUS_TRANSITION\"}")));

        LockResult result = client.lock(buildEvent("acc-1"));

        assertThat(result.status()).isEqualTo(Status.INVALID_TRANSITION);
        assertThat(result.httpStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("4xx 응답(409 제외) → FAILURE")
    void lock_4xx_returnsFailure() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse().withStatus(400)));

        LockResult result = client.lock(buildEvent("acc-1"));

        assertThat(result.status()).isEqualTo(Status.FAILURE);
        assertThat(result.httpStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("5xx 응답 → FAILURE (maxAttempts=1, 재시도 없음)")
    void lock_5xx_returnsFailure() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse().withStatus(503)));

        LockResult result = client.lock(buildEvent("acc-1"));

        assertThat(result.status()).isEqualTo(Status.FAILURE);
    }

    @Test
    @DisplayName("네트워크 오류 → FAILURE")
    void networkFault_returnsFailure() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        LockResult result = client.lock(buildEvent("acc-1"));

        assertThat(result.status()).isEqualTo(Status.FAILURE);
    }

    private static SuspiciousEvent buildEvent(String accountId) {
        return SuspiciousEvent.create(
                "evt-test-1",
                Tenants.DEFAULT_TENANT_ID,
                accountId,
                "CREDENTIAL_STUFFING",
                90,
                RiskLevel.AUTO_LOCK,
                null,
                "trigger-1",
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}

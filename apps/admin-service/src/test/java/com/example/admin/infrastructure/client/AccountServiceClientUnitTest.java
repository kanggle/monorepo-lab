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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccountServiceClient 단위 테스트")
class AccountServiceClientUnitTest {

    private WireMockServer wireMock;
    private AccountServiceClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        // Constructor explicitly sets HTTP_1_1 — no h2c fix needed.
        client = new AccountServiceClient(wireMock.baseUrl(), 3000, 5000, "");
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("search — 200 응답 → AccountSearchResponse(content) 반환")
    void search_200_returnsAccountSearchResponse() {
        wireMock.stubFor(get(urlPathEqualTo("/internal/accounts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"content\":[{\"id\":\"acc-1\",\"email\":\"foo@example.com\"," +
                                "\"status\":\"ACTIVE\",\"createdAt\":\"2026-01-01T00:00:00Z\"}]," +
                                "\"totalElements\":1,\"page\":0,\"size\":20,\"totalPages\":1}")));

        AccountServiceClient.AccountSearchResponse resp = client.search("foo@example.com");

        assertThat(resp.content()).hasSize(1);
        assertThat(resp.content().get(0).id()).isEqualTo("acc-1");
        assertThat(resp.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("search — 5xx 응답 → DownstreamFailureException")
    void search_5xx_throwsDownstreamFailureException() {
        wireMock.stubFor(get(urlPathEqualTo("/internal/accounts"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.search("foo@example.com"))
                .isInstanceOf(DownstreamFailureException.class)
                .isNotInstanceOf(NonRetryableDownstreamException.class);
    }

    @Test
    @DisplayName("getDetail — 200 응답 → AccountDetailResponse 반환")
    void getDetail_200_returnsAccountDetailResponse() {
        wireMock.stubFor(get(urlPathEqualTo("/internal/accounts/acc-1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"acc-1\",\"email\":\"foo@example.com\",\"status\":\"ACTIVE\"," +
                                "\"createdAt\":\"2026-01-01T00:00:00Z\"," +
                                "\"profile\":{\"displayName\":\"Alice\",\"phoneMasked\":\"***-1234\"}}")));

        AccountServiceClient.AccountDetailResponse resp = client.getDetail("acc-1");

        assertThat(resp.id()).isEqualTo("acc-1");
        assertThat(resp.profile().displayName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("getDetail — 4xx 응답 → NonRetryableDownstreamException")
    void getDetail_4xx_throwsNonRetryableDownstreamException() {
        wireMock.stubFor(get(urlPathEqualTo("/internal/accounts/acc-404"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"ACCOUNT_NOT_FOUND\"}")));

        assertThatThrownBy(() -> client.getDetail("acc-404"))
                .isInstanceOf(NonRetryableDownstreamException.class);
    }

    @Test
    @DisplayName("lock — 200 응답 → LockResponse(currentStatus=LOCKED) 반환")
    void lock_200_returnsLockResponse() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acc-1\",\"previousStatus\":\"ACTIVE\"," +
                                "\"currentStatus\":\"LOCKED\",\"lockedAt\":\"2026-01-01T00:00:00Z\"," +
                                "\"unlockedAt\":null}")));

        AccountServiceClient.LockResponse resp =
                client.lock("acc-1", "op-1", "ADMIN_LOCK", null, "idemp-1");

        assertThat(resp.currentStatus()).isEqualTo("LOCKED");
        assertThat(resp.accountId()).isEqualTo("acc-1");
    }

    @Test
    @DisplayName("lock — 4xx 응답 → NonRetryableDownstreamException")
    void lock_4xx_throwsNonRetryableDownstreamException() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse().withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"ACCOUNT_ALREADY_LOCKED\"}")));

        assertThatThrownBy(() -> client.lock("acc-1", "op-1", "ADMIN_LOCK", null, "idemp-1"))
                .isInstanceOf(NonRetryableDownstreamException.class);
    }

    @Test
    @DisplayName("lock — 5xx 응답 → DownstreamFailureException (NonRetryable 아님)")
    void lock_5xx_throwsDownstreamFailureException() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.lock("acc-1", "op-1", "ADMIN_LOCK", null, "idemp-1"))
                .isInstanceOf(DownstreamFailureException.class)
                .isNotInstanceOf(NonRetryableDownstreamException.class);
    }

    @Test
    @DisplayName("네트워크 오류 → DownstreamFailureException")
    void networkFault_throwsDownstreamFailureException() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertThatThrownBy(() -> client.lock("acc-1", "op-1", "ADMIN_LOCK", null, "idemp-1"))
                .isInstanceOf(DownstreamFailureException.class);
    }
}

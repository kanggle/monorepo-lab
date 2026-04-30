package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the Resilience4j configuration applied to {@link AccountServiceClient}:
 *
 *   - 5xx failures trigger retry up to {@code max-attempts=3}, then succeed
 *   - 4xx responses (wrapped as {@link NonRetryableDownstreamException}) skip retry
 *   - Repeated 5xx exhausts retries and surfaces {@link DownstreamFailureException}
 *
 * The test uses WireMock as the account-service stub and composes a Retry +
 * CircuitBreaker decorator around the real client — mirroring the runtime
 * annotation wiring declared in {@code application.yml}.
 */
class AccountServiceClientResilienceTest {

    private static WireMockServer wireMock;
    private static AccountServiceClient client;
    private static Retry retry;
    private static CircuitBreaker circuitBreaker;

    @BeforeAll
    static void startAll() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        client = new AccountServiceClient(
                "http://localhost:" + wireMock.port(),
                1000,
                2000,
                "test-token");

        // Mirror application.yml: maxAttempts=3, ignore NonRetryableDownstreamException
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(50))
                .ignoreExceptions(NonRetryableDownstreamException.class)
                .build();
        retry = Retry.of("accountService", retryConfig);

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50f)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .ignoreExceptions(NonRetryableDownstreamException.class)
                .build();
        circuitBreaker = CircuitBreaker.of("accountService", cbConfig);
    }

    @AfterAll
    static void stopAll() {
        wireMock.stop();
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
        circuitBreaker.reset();
    }

    private AccountServiceClient.LockResponse callLockWithResilience(String accountId, String idempotencyKey) {
        Supplier<AccountServiceClient.LockResponse> base =
                () -> client.lock(accountId, "op-1", "fraud", null, idempotencyKey);
        Supplier<AccountServiceClient.LockResponse> cbWrapped =
                CircuitBreaker.decorateSupplier(circuitBreaker, base);
        Supplier<AccountServiceClient.LockResponse> retryWrapped =
                Retry.decorateSupplier(retry, cbWrapped);
        return retryWrapped.get();
    }

    @Test
    void retry_config_max_attempts_is_3() {
        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @Test
    void retries_on_5xx_then_succeeds_on_third_attempt() {
        // Stub returns 503 on first two calls, 200 on the third.
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .inScenario("retry").whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("attempt2"));
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .inScenario("retry").whenScenarioStateIs("attempt2")
                .willReturn(aResponse().withStatus(502))
                .willSetStateTo("attempt3"));
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .inScenario("retry").whenScenarioStateIs("attempt3")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acc-1\",\"previousStatus\":\"ACTIVE\",\"currentStatus\":\"LOCKED\",\"lockedAt\":\"2026-01-01T00:00:00Z\"}")));

        AccountServiceClient.LockResponse resp = callLockWithResilience("acc-1", "idemp-1");

        assertThat(resp.currentStatus()).isEqualTo("LOCKED");
        wireMock.verify(3, com.github.tomakehurst.wiremock.client.WireMock
                .postRequestedFor(urlPathMatching("/internal/accounts/.*/lock")));
    }

    @Test
    void does_not_retry_on_4xx() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"VALIDATION_ERROR\",\"message\":\"bad\"}")));

        assertThatThrownBy(() -> callLockWithResilience("acc-2", "idemp-2"))
                .isInstanceOf(NonRetryableDownstreamException.class);

        wireMock.verify(1, com.github.tomakehurst.wiremock.client.WireMock
                .postRequestedFor(urlPathMatching("/internal/accounts/.*/lock")));
    }

    @Test
    void four_xx_populates_http_status_and_error_code_fields() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"ACCOUNT_NOT_FOUND\",\"message\":\"nope\"}")));

        assertThatThrownBy(() -> callLockWithResilience("acc-404", "idemp-404"))
                .isInstanceOfSatisfying(NonRetryableDownstreamException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(404);
                    assertThat(ex.getErrorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
                });
    }

    @Test
    void four_xx_with_nested_error_object_extracts_code() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse().withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"STATE_TRANSITION_INVALID\"}}")));

        assertThatThrownBy(() -> callLockWithResilience("acc-409", "idemp-409"))
                .isInstanceOfSatisfying(NonRetryableDownstreamException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(409);
                    assertThat(ex.getErrorCode()).isEqualTo("STATE_TRANSITION_INVALID");
                });
    }

    @Test
    void exhausts_retries_and_propagates_downstream_failure() {
        wireMock.stubFor(post(urlPathMatching("/internal/accounts/.*/lock"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> callLockWithResilience("acc-3", "idemp-3"))
                .isInstanceOf(DownstreamFailureException.class);

        wireMock.verify(3, com.github.tomakehurst.wiremock.client.WireMock
                .postRequestedFor(urlPathMatching("/internal/accounts/.*/lock")));
    }
}

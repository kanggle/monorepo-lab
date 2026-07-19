package com.wms.notification.adapter.outbound.slack;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * Circuit-breaker <em>transition</em> coverage for the {@code slack} breaker
 * that wraps {@link SlackChannelAdapter#send} (TASK-BE-528 AC-1).
 *
 * <p>The existing {@code SlackChannelAdapterWireMockTest} builds the adapter
 * with {@code new SlackChannelAdapter(...)}, which BYPASSES the Spring AOP
 * proxy — so the {@code @CircuitBreaker(name = "slack")} annotation is never
 * active there and its javadoc claim that transitions are "verified in
 * @SpringBootTest suites" was false (no such suite existed). This test drives
 * the <em>real</em> adapter call through a real Resilience4j
 * {@link CircuitBreaker} created from a {@link CircuitBreakerRegistry}, so the
 * genuine state machine (CLOSED → OPEN → HALF_OPEN → CLOSED) is exercised
 * against WireMock — no Spring context / Docker required, fully deterministic.
 *
 * <h2>Config faithfulness</h2>
 *
 * <p>The {@link CircuitBreakerConfig} below mirrors the production
 * {@code resilience4j.circuitbreaker.instances.slack} block in
 * {@code application.yml:89-99} EXACTLY, with the single exception of
 * {@code waitDurationInOpenState} which is shrunk from {@code 10s} to
 * {@code 100ms} so the HALF-OPEN recovery leg stays fast (no real 10s sleep) —
 * this is the "test profile shrink" AC-1 asks for. Note the production breaker
 * instance declares NO {@code ignoreExceptions} (only the {@code retry}
 * instance ignores {@link com.wms.notification.domain.error.ChannelPermanentFailureException});
 * this test therefore uses a RETRYABLE 5xx (503) to drive failures, which
 * counts toward the failure rate under any configuration.
 */
@TestInstance(Lifecycle.PER_CLASS)
class SlackChannelAdapterCircuitBreakerTest {

    private static final String ALIAS = "wms-alerts";
    private static final String PATH = "/services/T0/B0/X";
    private static final String PAYLOAD = "{\"eventType\":\"inventory.low-stock-detected\"}";

    private WireMockServer wireMock;
    private SlackChannelAdapter adapter;
    private CircuitBreaker breaker;

    /** Mirror of {@code application.yml:89-99}, waitDuration shrunk to 100ms. */
    private static CircuitBreakerConfig slackConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.TIME_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(100)) // prod: 10s
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
    }

    @BeforeAll
    void start() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    void stop() {
        wireMock.stop();
    }

    @BeforeEach
    void wire() {
        wireMock.resetAll();
        SlackChannelProperties properties = new SlackChannelProperties();
        SlackChannelProperties.ChannelConfig cfg = new SlackChannelProperties.ChannelConfig();
        cfg.setWebhookUrl(wireMock.baseUrl() + PATH);
        properties.getSlack().put(ALIAS, cfg);
        adapter = new SlackChannelAdapter(properties, new ObjectMapper());
        breaker = CircuitBreakerRegistry.of(slackConfig()).circuitBreaker("slack");
    }

    private void sendThroughBreaker() {
        breaker.executeRunnable(() -> adapter.send(ALIAS, PAYLOAD));
    }

    private void stubStatus(int status) {
        wireMock.resetAll();
        wireMock.stubFor(post(urlPathEqualTo(PATH))
                .willReturn(aResponse().withStatus(status)));
    }

    @Test
    @DisplayName("CLOSED → OPEN once minimumNumberOfCalls failing 5xx cross the 50% threshold; "
            + "further calls short-circuit with CallNotPermittedException")
    void closedToOpenShortCircuits() {
        stubStatus(503); // retryable transient failure — counts toward failure rate

        // minimumNumberOfCalls = 5. Five failing in-window calls → 100% failure
        // rate ≥ 50% threshold → breaker transitions CLOSED → OPEN.
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(this::sendThroughBreaker)
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(CallNotPermittedException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // The 6th call is short-circuited: CallNotPermittedException and the
        // request NEVER reaches WireMock (only the first 5 did).
        assertThatThrownBy(this::sendThroughBreaker)
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(wireMock.getAllServeEvents()).hasSize(5);
    }

    @Test
    @DisplayName("OPEN → HALF_OPEN → CLOSED recovery: after waitDurationInOpenState a probe "
            + "is admitted and permittedNumberOfCallsInHalfOpenState successes re-close the breaker")
    void openToHalfOpenToClosedRecovers() {
        // Drive the breaker OPEN first.
        stubStatus(503);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(this::sendThroughBreaker).isInstanceOf(RuntimeException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(this::sendThroughBreaker).isInstanceOf(CallNotPermittedException.class);

        // Wait out waitDurationInOpenState (100ms) deterministically — no sleep,
        // Awaitility pollDelay. Automatic OPEN→HALF_OPEN is disabled by default,
        // so the state flips on the NEXT admitted call after the window elapses.
        await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofMillis(500)).until(() -> true);

        stubStatus(200); // vendor recovered
        // permittedNumberOfCallsInHalfOpenState = 3. First probe admits and flips
        // OPEN → HALF_OPEN; three successes then evaluate → CLOSED.
        sendThroughBreaker();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        sendThroughBreaker();
        sendThroughBreaker();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("HALF_OPEN → OPEN when the probe calls keep failing")
    void halfOpenBackToOpenOnContinuedFailure() {
        stubStatus(503);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(this::sendThroughBreaker).isInstanceOf(RuntimeException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofMillis(500)).until(() -> true);

        // Probes still fail (503) → HALF_OPEN re-OPENs after the permitted probes.
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(this::sendThroughBreaker)
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(CallNotPermittedException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}

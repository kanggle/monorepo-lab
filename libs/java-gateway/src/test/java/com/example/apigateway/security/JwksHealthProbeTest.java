package com.example.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Exceptions;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link JwksHealthProbe}'s fail-fast contract:
 *
 * <ul>
 *   <li>Probe succeeds (JWKS returns 200) → app context is NOT closed.</li>
 *   <li>Probe transiently fails (503) but recovers within the window → context NOT closed.</li>
 *   <li>Probe fails for the entire window → {@code applicationContext.close()} is called.</li>
 *   <li>TASK-MONO-489: a 404 gets a small bounded retry budget (not an immediate close) but
 *       still closes if that budget runs out — see the {@code ...ClientErrorBudget...} and
 *       {@code recoversFromConnectionErrorThenTransient404...} tests below.</li>
 * </ul>
 *
 * <p>Plus the guard that made moving this class here safe at all — see
 * {@link #isNotAComponentSoItCannotLeakIntoAScanningGateway()}.
 */
class JwksHealthProbeTest {

    /**
     * The scm and fan copies carried {@code @Component}. Moving the class into
     * {@code libs/java-gateway} with the annotation intact would have registered it in every
     * gateway that scans {@code com.example.apigateway} — including <strong>wms, which has
     * never had a JWKS startup probe</strong>. wms would have silently gained a boot-time
     * dependency on the IdP being reachable: a behaviour change, arriving under the banner of
     * de-duplication, which is exactly what ADR-MONO-048 § D6 forbids.
     *
     * <p>Registration is therefore opt-in — each gateway that wants the probe declares a
     * {@code @Bean}. Re-adding a stereotype here would silently re-open that door, so this
     * test closes it: it fails the build instead (TASK-MONO-357).
     */
    @Test
    void isNotAComponentSoItCannotLeakIntoAScanningGateway() {
        assertThat(JwksHealthProbe.class.getAnnotations())
                .as("a stereotype here registers the probe in EVERY gateway that scans this "
                        + "package — wms scans it and has never had a JWKS startup probe")
                .noneMatch(a -> a.annotationType().getName()
                        .startsWith("org.springframework.stereotype")
                        || a.annotationType().getName()
                                .equals("org.springframework.context.annotation.Configuration"));
    }

    private MockWebServer jwksServer;
    private static final String JWKS_BODY = "{\"keys\":[]}";
    private static final ApplicationReadyEvent FAKE_EVENT =
            mock(ApplicationReadyEvent.class);

    @BeforeEach
    void start() throws IOException {
        jwksServer = new MockWebServer();
        jwksServer.start();
    }

    @AfterEach
    void stop() throws IOException {
        jwksServer.shutdown();
    }

    @Test
    void doesNotCloseContextWhenJwksReturns200() {
        jwksServer.enqueue(jwksOk());

        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        JwksHealthProbe probe = new JwksHealthProbe(
                jwksUrl(),
                30,
                ctx,
                WebClient.builder());

        probe.onApplicationEvent(FAKE_EVENT);

        verify(ctx, never()).close();
    }

    @Test
    void doesNotCloseContextWhenJwksRecoversAfterTransient503() {
        // Two transient 503s, then success — well within the 30s window.
        AtomicInteger calls = new AtomicInteger();
        jwksServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                int n = calls.incrementAndGet();
                if (n <= 2) {
                    return new MockResponse().setResponseCode(503);
                }
                return jwksOk();
            }
        });

        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        JwksHealthProbe probe = new JwksHealthProbe(
                jwksUrl(),
                30,
                ctx,
                WebClient.builder());

        probe.onApplicationEvent(FAKE_EVENT);

        verify(ctx, never()).close();
        assertThat(calls.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void closesContextWhenJwksFailsForTheEntireWindow() {
        // Permanently 503 — backoff schedule (1+2+4=7s) within the 5-second window
        // ensures we exhaust retries quickly.
        jwksServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(503);
            }
        });

        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        JwksHealthProbe probe = new JwksHealthProbe(
                jwksUrl(),
                5, // short window so the test runs in ~5s rather than ~30s
                ctx,
                WebClient.builder());

        probe.onApplicationEvent(FAKE_EVENT);

        verify(ctx, atLeastOnce()).close();
    }

    @Test
    void closesContextWhenJwksHostUnreachable() {
        // Point at a port that nothing listens on. WebClient connection refused
        // is a transient error; backoff exhausts inside the configured window.
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        JwksHealthProbe probe = new JwksHealthProbe(
                "http://127.0.0.1:1/oauth2/jwks",
                3,
                ctx,
                WebClient.builder());

        probe.onApplicationEvent(FAKE_EVENT);

        verify(ctx, atLeastOnce()).close();
    }

    @Test
    void closesContextAfterClientErrorBudgetExhaustedOnPersistent404() {
        // TASK-MONO-489 adversarial case (mutation-style): a 404 now gets a small bounded
        // retry budget (3 attempts, 1s apart) for the case where the backend is mid-restart.
        // If it is STILL 404 after that budget, it is a genuine misconfiguration (wrong
        // URL/auth) and must fail fast — critically, it must NOT fall through to the much
        // larger general-tier backoff (1+2+4+8+16=31s), which exists for real transient
        // outages, not a confirmed-permanent 404. The elapsed-time assertion is what proves
        // that: bounded (>= the 3s client-error budget) but nowhere near the 31s ceiling.
        AtomicInteger calls = new AtomicInteger();
        jwksServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                calls.incrementAndGet();
                return new MockResponse().setResponseCode(404);
            }
        });

        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        JwksHealthProbe probe = new JwksHealthProbe(
                jwksUrl(),
                30,
                ctx,
                WebClient.builder());

        long start = System.currentTimeMillis();
        probe.onApplicationEvent(FAKE_EVENT);
        long elapsed = System.currentTimeMillis() - start;

        verify(ctx, atLeastOnce()).close();
        assertThat(calls.get())
                .as("1 initial attempt + 3 bounded client-error retries, no more")
                .isEqualTo(4);
        assertThat(elapsed)
                .as("must fail fast off the small client-error budget, not the 31s general "
                        + "backoff schedule")
                .isLessThan(10_000);
    }

    @Test
    void recoversFromConnectionErrorThenTransient404ThenSucceeds() {
        // Reproduces the 2026-07-29 fan-platform incident (TASK-MONO-489): the JWKS backend
        // is mid-restart. First attempt hits a transient 5xx (already retried, pre-fix, by
        // the general tier — the same shape as doesNotCloseContextWhenJwksRecoversAfter
        // Transient503 above). Second attempt gets routed but the backend has not registered
        // the route yet (404 — previously terminal on the spot). Third attempt succeeds once
        // the backend is fully up. Before this task, the second attempt alone would have
        // closed the context.
        AtomicInteger calls = new AtomicInteger();
        jwksServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                int n = calls.incrementAndGet();
                if (n == 1) {
                    return new MockResponse().setResponseCode(503);
                }
                if (n == 2) {
                    return new MockResponse().setResponseCode(404);
                }
                return jwksOk();
            }
        });

        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        JwksHealthProbe probe = new JwksHealthProbe(
                jwksUrl(),
                30,
                ctx,
                WebClient.builder());

        probe.onApplicationEvent(FAKE_EVENT);

        verify(ctx, never()).close();
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void probeMonoSucceedsAfterTwoBounded404sWithinClientErrorBudget() {
        // Same recovery shape as above but asserted directly on the pure probe() Mono via
        // StepVerifier (Failure Scenarios note: boot-time timing defects aren't caught by
        // ordinary unit tests, so probe()'s Mono-returning shape must be independently
        // verifiable without going through onApplicationEvent's block()/context-close side
        // effects).
        AtomicInteger calls = new AtomicInteger();
        jwksServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                int n = calls.incrementAndGet();
                if (n <= 2) {
                    return new MockResponse().setResponseCode(404);
                }
                return jwksOk();
            }
        });

        JwksHealthProbe probe = new JwksHealthProbe(
                jwksUrl(), 30, mock(ConfigurableApplicationContext.class), WebClient.builder());

        StepVerifier.create(probe.probe())
                .expectNext(JWKS_BODY)
                .verifyComplete();

        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void probeMonoErrorsAsRetryExhaustedOncePersistent404BudgetRunsOut() {
        // Mutation guard for the isTransient() boundary: if Exceptions.isRetryExhausted(t) is
        // ever dropped or inverted, a persistent 404 stops being terminal to the general tier
        // and this Mono either succeeds after 5 more backed-off attempts or times out well
        // past the bounded window asserted here.
        jwksServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(404);
            }
        });

        JwksHealthProbe probe = new JwksHealthProbe(
                jwksUrl(), 30, mock(ConfigurableApplicationContext.class), WebClient.builder());

        StepVerifier.create(probe.probe())
                .expectErrorMatches(Exceptions::isRetryExhausted)
                .verify(Duration.ofSeconds(8));
    }

    private static MockResponse jwksOk() {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(JWKS_BODY);
    }

    private String jwksUrl() {
        return "http://" + jwksServer.getHostName() + ":" + jwksServer.getPort()
                + "/oauth2/jwks";
    }
}

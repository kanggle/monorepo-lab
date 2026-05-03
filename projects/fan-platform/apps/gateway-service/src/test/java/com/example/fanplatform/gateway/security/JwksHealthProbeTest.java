package com.example.fanplatform.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
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

/**
 * Unit tests for {@link JwksHealthProbe}'s fail-fast contract:
 *
 * <ul>
 *   <li>Probe succeeds (JWKS returns 200) → app context is NOT closed.</li>
 *   <li>Probe transiently fails (503) but recovers within the window → context NOT closed.</li>
 *   <li>Probe fails for the entire window → {@code applicationContext.close()} is called.</li>
 * </ul>
 */
class JwksHealthProbeTest {

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
                "http://127.0.0.1:1/.well-known/jwks.json",
                3,
                ctx,
                WebClient.builder());

        probe.onApplicationEvent(FAKE_EVENT);

        verify(ctx, atLeastOnce()).close();
    }

    @Test
    void closesContextImmediatelyOn404() {
        // 4xx is a configuration problem (wrong URL / auth), not a transient
        // network issue. The probe must not retry pointlessly — it should give up
        // and trigger fail-fast.
        jwksServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
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
        // Should give up well before the 30s timeout because 4xx is non-transient.
        assertThat(elapsed).isLessThan(15_000);
    }

    private static MockResponse jwksOk() {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(JWKS_BODY);
    }

    private String jwksUrl() {
        return "http://" + jwksServer.getHostName() + ":" + jwksServer.getPort()
                + "/.well-known/jwks.json";
    }
}

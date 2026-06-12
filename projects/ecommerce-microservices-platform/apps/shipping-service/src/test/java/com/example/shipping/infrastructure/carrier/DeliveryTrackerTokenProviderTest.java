package com.example.shipping.infrastructure.carrier;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit test for {@link DeliveryTrackerTokenProvider} (TASK-BE-364 / AC-2 / AC-4) over MockWebServer:
 * the OAuth2 {@code client_credentials} token is cached across calls (one HTTP fetch), re-fetched
 * after expiry, and every failure mode is best-effort empty (never throws). No real tracker.delivery.
 */
class DeliveryTrackerTokenProviderTest {

    private MockWebServer auth;

    @BeforeEach
    void setUp() throws IOException {
        auth = new MockWebServer();
        auth.start();
    }

    @AfterEach
    void tearDown() {
        try {
            auth.shutdown();
        } catch (Exception ignored) {
            // already shut down
        }
    }

    private DeliveryTrackerProperties props() {
        return new DeliveryTrackerProperties(
                "http://" + auth.getHostName() + ":" + auth.getPort(),
                "http://unused/graphql", "client-abc", "secret-xyz");
    }

    private DeliveryTrackerTokenProvider provider(DeliveryTrackerProperties props, Clock clock) {
        return new DeliveryTrackerTokenProvider(props, 2000, 5000, new SimpleMeterRegistry(), clock);
    }

    @Test
    void cachesTokenAcrossCalls_singleHttpFetch() throws Exception {
        auth.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"tok-1\",\"expires_in\":3600}"));

        DeliveryTrackerTokenProvider provider = provider(props(), Clock.systemUTC());

        assertThat(provider.getToken()).contains("tok-1");
        assertThat(provider.getToken()).contains("tok-1"); // reused from cache

        // AC-2: only ONE token HTTP call despite two getToken() calls.
        assertThat(auth.getRequestCount()).isEqualTo(1);

        RecordedRequest req = auth.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getHeader("Authorization"))
                .isEqualTo("Basic " + Base64.getEncoder().encodeToString("client-abc:secret-xyz".getBytes()));
        assertThat(req.getBody().readUtf8()).isEqualTo("grant_type=client_credentials");
        assertThat(req.getHeader("Content-Type")).startsWith("application/x-www-form-urlencoded");
    }

    @Test
    void reFetchesAfterExpiry() {
        auth.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"tok-1\",\"expires_in\":120}"));
        auth.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"tok-2\",\"expires_in\":120}"));

        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-06-13T00:00:00Z"));
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId z) { return this; }
            @Override public Instant instant() { return now.get(); }
        };

        DeliveryTrackerTokenProvider provider = provider(props(), clock);
        assertThat(provider.getToken()).contains("tok-1");

        // expires_in=120, margin=60 → window 60s; advance past it → re-fetch.
        now.set(now.get().plus(Duration.ofSeconds(90)));
        assertThat(provider.getToken()).contains("tok-2");
        assertThat(auth.getRequestCount()).isEqualTo(2);
    }

    @Test
    void noExpiresIn_reFetchesEveryCall() {
        auth.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"access_token\":\"tok-1\"}"));
        auth.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"access_token\":\"tok-2\"}"));

        DeliveryTrackerTokenProvider provider = provider(props(), Clock.systemUTC());
        assertThat(provider.getToken()).contains("tok-1");
        assertThat(provider.getToken()).contains("tok-2");
        assertThat(auth.getRequestCount()).isEqualTo(2);
    }

    @Test
    void tokenFailure_returnsEmpty_neverThrows() {
        auth.enqueue(new MockResponse().setResponseCode(401));

        DeliveryTrackerTokenProvider provider = provider(props(), Clock.systemUTC());

        Optional<String>[] result = new Optional[1];
        assertThatCode(() -> result[0] = provider.getToken()).doesNotThrowAnyException();
        assertThat(result[0]).isEmpty();
    }

    @Test
    void blankCredential_returnsEmpty_noHttpCall() {
        DeliveryTrackerProperties blank = new DeliveryTrackerProperties(
                "http://" + auth.getHostName() + ":" + auth.getPort(), "http://unused/graphql", "", "");

        assertThat(provider(blank, Clock.systemUTC()).getToken()).isEmpty();
        assertThat(auth.getRequestCount()).isZero();
    }
}

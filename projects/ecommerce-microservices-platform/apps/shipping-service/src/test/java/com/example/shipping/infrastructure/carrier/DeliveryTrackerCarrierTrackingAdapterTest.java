package com.example.shipping.infrastructure.carrier;

import com.example.shipping.application.port.CarrierTrackingPort.CarrierTrackingSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit test for {@link DeliveryTrackerCarrierTrackingAdapter} (TASK-BE-364 / external-integrations.md
 * § 1 + § 8) over MockWebServer — no real tracker.delivery. Covers: token + GraphQL 200 (mapped &
 * unmapped raw code both relayed verbatim — mapping is the caller's job), GraphQL {@code errors[]},
 * {@code track:null}, token 401 (no GraphQL call), and transport failure — all best-effort empty
 * (never throws, AC-4).
 */
class DeliveryTrackerCarrierTrackingAdapterTest {

    private MockWebServer auth;
    private MockWebServer graphql;

    @BeforeEach
    void setUp() throws IOException {
        auth = new MockWebServer();
        auth.start();
        graphql = new MockWebServer();
        graphql.start();
    }

    @AfterEach
    void tearDown() {
        shutdownQuietly(auth);
        shutdownQuietly(graphql);
    }

    private static void shutdownQuietly(MockWebServer server) {
        try {
            server.shutdown();
        } catch (Exception ignored) {
            // already shut down
        }
    }

    private String url(MockWebServer server) {
        return "http://" + server.getHostName() + ":" + server.getPort();
    }

    /** Properties pointing the token + graphql clients at the two MockWebServers. */
    private DeliveryTrackerProperties props() {
        return new DeliveryTrackerProperties(url(auth), url(graphql), "client-abc", "secret-xyz");
    }

    private DeliveryTrackerCarrierTrackingAdapter adapter(DeliveryTrackerProperties props) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DeliveryTrackerTokenProvider tokenProvider =
                new DeliveryTrackerTokenProvider(props, 2000, 5000, registry, Clock.systemUTC());
        return new DeliveryTrackerCarrierTrackingAdapter(props, tokenProvider, 2000, 5000, registry);
    }

    private void enqueueToken() {
        auth.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"tok-1\",\"expires_in\":3600}"));
    }

    @Test
    void mappedStatus_returnsSnapshot_sendsBearerAndQuery() throws Exception {
        enqueueToken();
        graphql.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"track\":{\"lastEvent\":{\"time\":\"2026-06-13T00:00:00Z\","
                        + "\"status\":{\"code\":\"DELIVERED\"}}}}}"));

        Optional<CarrierTrackingSnapshot> result =
                adapter(props()).fetchLatest("kr.cjlogistics", "TRK-1");

        assertThat(result).map(CarrierTrackingSnapshot::rawStatus).contains("DELIVERED");

        RecordedRequest graphReq = graphql.takeRequest();
        assertThat(graphReq.getMethod()).isEqualTo("POST");
        assertThat(graphReq.getHeader("Authorization")).isEqualTo("Bearer tok-1");
        String body = graphReq.getBody().readUtf8();
        assertThat(body).contains("GetTrackLastEvent");
        assertThat(body).contains("kr.cjlogistics");
        assertThat(body).contains("TRK-1");
    }

    @Test
    void unmappedRawStatus_isRelayedVerbatim_noMappingInAdapter() {
        enqueueToken();
        graphql.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"track\":{\"lastEvent\":{\"status\":{\"code\":\"EXCEPTION\"}}}}}"));

        // The adapter relays the raw code; the caller decides it is unmapped (CarrierStatusMapper).
        assertThat(adapter(props()).fetchLatest("kr.hanjin", "TRK-2"))
                .map(CarrierTrackingSnapshot::rawStatus).contains("EXCEPTION");
    }

    @Test
    void graphQlErrors_returnsEmpty() {
        enqueueToken();
        graphql.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"errors\":[{\"message\":\"NOT_FOUND\"}],\"data\":null}"));

        assertThat(adapter(props()).fetchLatest("kr.lotte", "TRK-3")).isEmpty();
    }

    @Test
    void trackNull_returnsEmpty() {
        enqueueToken();
        graphql.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"track\":null}}"));

        assertThat(adapter(props()).fetchLatest("kr.epost", "TRK-4")).isEmpty();
    }

    @Test
    void noLastEvent_returnsEmpty() {
        enqueueToken();
        graphql.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"track\":{\"lastEvent\":null}}}"));

        assertThat(adapter(props()).fetchLatest("kr.epost", "TRK-5")).isEmpty();
    }

    @Test
    void tokenFailure_returnsEmpty_noGraphQlCall() {
        auth.enqueue(new MockResponse().setResponseCode(401));

        Optional<CarrierTrackingSnapshot>[] result = new Optional[1];
        assertThatCode(() -> result[0] = adapter(props()).fetchLatest("kr.cjlogistics", "TRK-6"))
                .doesNotThrowAnyException();
        assertThat(result[0]).isEmpty();
        // AC-4 / § 1.6: token failure → NO GraphQL call.
        assertThat(graphql.getRequestCount()).isZero();
    }

    @Test
    void graphQlTransportDown_returnsEmpty_neverThrows() throws IOException {
        enqueueToken();
        graphql.shutdown(); // connection refused on the GraphQL call

        Optional<CarrierTrackingSnapshot>[] result = new Optional[1];
        assertThatCode(() -> result[0] = adapter(props()).fetchLatest("kr.cjlogistics", "TRK-7"))
                .doesNotThrowAnyException();
        assertThat(result[0]).isEmpty();
    }

    @Test
    void blankCredential_returnsEmpty_noOutboundCall() {
        DeliveryTrackerProperties blank =
                new DeliveryTrackerProperties(url(auth), url(graphql), "", "");

        assertThat(adapter(blank).fetchLatest("kr.cjlogistics", "TRK-8")).isEmpty();
        assertThat(auth.getRequestCount()).isZero();
        assertThat(graphql.getRequestCount()).isZero();
    }
}

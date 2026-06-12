package com.example.shipping.infrastructure.carrier;

import com.example.shipping.application.port.CarrierTrackingPort.CarrierTrackingSnapshot;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit test for {@link HttpCarrierTrackingAdapter} (TASK-BE-293): maps a carrier 2xx
 * {@code {status}} to a snapshot (sending the bearer header + carrier/trackingNumber
 * query), and is best-effort/never-throw on every failure mode (5xx, server down, 2xx
 * without a status). RestClient points at a MockWebServer — no real carrier contacted.
 */
class HttpCarrierTrackingAdapterTest {

    private MockWebServer carrier;

    @BeforeEach
    void setUp() throws IOException {
        carrier = new MockWebServer();
        carrier.start();
    }

    @AfterEach
    void tearDown() {
        try {
            carrier.shutdown();
        } catch (Exception ignored) {
            // already shut down (the server-down test shuts it itself)
        }
    }

    private String baseUrl() {
        return "http://" + carrier.getHostName() + ":" + carrier.getPort();
    }

    private HttpCarrierTrackingAdapter adapter(String baseUrl) {
        return new HttpCarrierTrackingAdapter(baseUrl, "test-key", 2000, 5000);
    }

    @Test
    void mapsStatus_sendsBearerAndQuery() throws Exception {
        carrier.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"DELIVERED\"}"));

        Optional<CarrierTrackingSnapshot> result = adapter(baseUrl()).fetchLatest("CJ", "TRK-1");

        assertThat(result).map(CarrierTrackingSnapshot::rawStatus).contains("DELIVERED");

        RecordedRequest req = carrier.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/track?carrier=CJ&trackingNumber=TRK-1");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-key");
    }

    @Test
    void relaysAggregatorUnifiedStatusVerbatim() throws Exception {
        // The aggregator (ADR-007 D2) returns its own unified status code (here a Korean
        // token); the adapter relays it raw — mapping + unmapped-observation happen in the
        // caller (RefreshTrackingService / CarrierStatusObserver), keeping this a pure ACL.
        carrier.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"배송완료\"}"));

        Optional<CarrierTrackingSnapshot> result = adapter(baseUrl()).fetchLatest("AUTO", "TRK-9");

        assertThat(result).map(CarrierTrackingSnapshot::rawStatus).contains("배송완료");
        RecordedRequest req = carrier.takeRequest();
        assertThat(req.getPath()).isEqualTo("/track?carrier=AUTO&trackingNumber=TRK-9");
    }

    @Test
    void relaysUnmappedAggregatorStatusVerbatim_noMappingInAdapter() throws Exception {
        // An aggregator code the mapping table does not cover is still relayed (the adapter
        // does not map); the caller decides it is unmapped and increments the counter.
        carrier.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"통관보류\"}"));

        Optional<CarrierTrackingSnapshot> result = adapter(baseUrl()).fetchLatest("AUTO", "TRK-9");

        assertThat(result).map(CarrierTrackingSnapshot::rawStatus).contains("통관보류");
        carrier.takeRequest();
    }

    @Test
    void twoXxWithoutStatus_returnsEmpty() {
        carrier.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"foo\":\"bar\"}"));

        assertThat(adapter(baseUrl()).fetchLatest("CJ", "TRK-1")).isEmpty();
    }

    @Test
    void serverError_returnsEmpty_neverThrows() {
        carrier.enqueue(new MockResponse().setResponseCode(503));

        Optional<CarrierTrackingSnapshot>[] result = new Optional[1];
        assertThatCode(() -> result[0] = adapter(baseUrl()).fetchLatest("CJ", "TRK-1"))
                .doesNotThrowAnyException();
        assertThat(result[0]).isEmpty();
    }

    @Test
    void carrierDown_returnsEmpty_neverThrows() throws IOException {
        String baseUrl = baseUrl();
        carrier.shutdown(); // connection refused on the next call

        Optional<CarrierTrackingSnapshot>[] result = new Optional[1];
        assertThatCode(() -> result[0] = adapter(baseUrl).fetchLatest("CJ", "TRK-1"))
                .doesNotThrowAnyException();
        assertThat(result[0]).isEmpty();
    }
}

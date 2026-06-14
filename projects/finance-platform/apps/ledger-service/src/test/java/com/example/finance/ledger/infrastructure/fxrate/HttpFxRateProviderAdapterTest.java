package com.example.finance.ledger.infrastructure.fxrate;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.FxRateProviderPort.RateQuote;
import com.example.finance.ledger.domain.money.Currency;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit/slice test for {@link HttpFxRateProviderAdapter} (AC-4 — best-effort, never-throw). Uses
 * {@link MockWebServer} (already a test dependency for the JWKS stand-in). Covers: 2xx → parsed
 * quote; 5xx → empty (no throw); connection-refused (server shut down) → empty (no throw); a
 * blank base URL → empty. {@code ResilienceClientFactory.buildRestClient} is exercised (the adapter
 * never uses {@code new RestTemplate()}).
 */
class HttpFxRateProviderAdapterTest {

    private static final Instant FALLBACK_NOW = Instant.parse("2026-06-15T00:00:00Z");
    private final ClockPort clock = () -> FALLBACK_NOW;

    private MockWebServer server;

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.shutdown();
        }
    }

    private HttpFxRateProviderAdapter adapterFor(String baseUrl) {
        FxRateFeedProperties props = new FxRateFeedProperties();
        props.setMode("http");
        props.getHttp().setBaseUrl(baseUrl);
        props.getHttp().setConnectTimeoutMs(1_000);
        props.getHttp().setReadTimeoutMs(2_000);
        return new HttpFxRateProviderAdapter(props, clock);
    }

    @Test
    void parsesQuoteOn2xx() throws IOException {
        server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"rate\":\"1300.0\",\"asOf\":\"2026-06-14T00:00:00Z\"}"));
        server.start();
        HttpFxRateProviderAdapter adapter = adapterFor(server.url("/").toString());

        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.USD);

        assertThat(quote).isPresent();
        assertThat(quote.get().rate()).isEqualByComparingTo("1300.0");
        assertThat(quote.get().asOf()).isEqualTo(Instant.parse("2026-06-14T00:00:00Z"));
        assertThat(quote.get().source()).startsWith("http:");
    }

    @Test
    void fallsBackToClockWhenAsOfOmitted() throws IOException {
        server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"rate\":\"1450.0\"}"));
        server.start();
        HttpFxRateProviderAdapter adapter = adapterFor(server.url("/").toString());

        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.EUR);

        assertThat(quote).isPresent();
        assertThat(quote.get().asOf()).isEqualTo(FALLBACK_NOW);
    }

    @Test
    void returnsEmptyOn5xxWithoutThrowing() throws IOException {
        server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(500));
        server.start();
        HttpFxRateProviderAdapter adapter = adapterFor(server.url("/").toString());

        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.USD);

        assertThat(quote).isEmpty();
    }

    @Test
    void returnsEmptyOnConnectionRefusedWithoutThrowing() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        server.shutdown();   // nothing is now listening → connection refused
        server = null;       // already shut down; skip tearDown double-shutdown
        HttpFxRateProviderAdapter adapter = adapterFor(baseUrl);

        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.USD);

        assertThat(quote).isEmpty();
    }

    @Test
    void returnsEmptyForBlankBaseUrl() {
        HttpFxRateProviderAdapter adapter = adapterFor("  ");

        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.USD);

        assertThat(quote).isEmpty();
    }
}

package com.example.finance.ledger.infrastructure.fxrate;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.FxRateProviderPort.RateQuote;
import com.example.finance.ledger.domain.money.Currency;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit/slice test for {@link RealFxRateProviderAdapter} (TASK-FIN-BE-038 — Frankfurter, best-effort
 * never-throw). Uses {@link MockWebServer} (a plain test dependency, NOT Testcontainers — runs in
 * the default {@code test} task). Covers: a Frankfurter-shaped 2xx body → parsed quote with the
 * correct rate/asOf/source AND the recorded request locks the direction mapping
 * ({@code from=USD&to=KRW}); plus never-throw cases (5xx, missing {@code rates}, missing {@code to}
 * key, malformed {@code date} → clock-fallback asOf, connection refused, blank base URL).
 */
class RealFxRateProviderAdapterTest {

    private static final Instant FALLBACK_NOW = Instant.parse("2026-06-15T00:00:00Z");
    private final ClockPort clock = () -> FALLBACK_NOW;

    private MockWebServer server;

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.shutdown();
        }
    }

    private RealFxRateProviderAdapter adapterFor(String baseUrl) {
        FxRateFeedProperties props = new FxRateFeedProperties();
        props.setMode("real");
        props.getReal().setBaseUrl(baseUrl);
        props.getReal().setConnectTimeoutMs(1_000);
        props.getReal().setReadTimeoutMs(2_000);
        return new RealFxRateProviderAdapter(props, clock);
    }

    @Test
    void parsesQuoteAndLocksDirectionOn2xx() throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-06-16\","
                        + "\"rates\":{\"KRW\":1361.23}}"));
        server.start();
        RealFxRateProviderAdapter adapter = adapterFor(server.url("/").toString());

        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.USD);

        assertThat(quote).isPresent();
        assertThat(quote.get().rate()).isEqualByComparingTo("1361.23");
        assertThat(quote.get().asOf()).isEqualTo(Instant.parse("2026-06-16T00:00:00Z"));
        assertThat(quote.get().source()).startsWith("real:");

        // Direction CRUX: latestQuote(base=KRW, foreign=USD) must request from=USD&to=KRW.
        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getPath()).startsWith("/latest");
        assertThat(recorded.getPath()).contains("from=USD");
        assertThat(recorded.getPath()).contains("to=KRW");
    }

    @Test
    void returnsEmptyOn5xxWithoutThrowing() throws IOException {
        server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(500));
        server.start();
        RealFxRateProviderAdapter adapter = adapterFor(server.url("/").toString());

        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.USD);

        assertThat(quote).isEmpty();
    }

    @Test
    void returnsEmptyWhenRatesKeyMissing() throws IOException {
        server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-06-16\"}"));
        server.start();
        RealFxRateProviderAdapter adapter = adapterFor(server.url("/").toString());

        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.USD);

        assertThat(quote).isEmpty();
    }

    @Test
    void returnsEmptyWhenForeignCodeNotQuoted() throws IOException {
        // rates map present but lacks the requested `to` (base) code → unsupported pair.
        server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-06-16\","
                        + "\"rates\":{\"EUR\":0.92}}"));
        server.start();
        RealFxRateProviderAdapter adapter = adapterFor(server.url("/").toString());

        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.USD);

        assertThat(quote).isEmpty();
    }

    @Test
    void fallsBackToClockOnMalformedDateButStillReturnsRate() throws IOException {
        server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"amount\":1.0,\"base\":\"USD\",\"date\":\"not-a-date\","
                        + "\"rates\":{\"KRW\":1361.23}}"));
        server.start();
        RealFxRateProviderAdapter adapter = adapterFor(server.url("/").toString());

        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.USD);

        assertThat(quote).isPresent();
        assertThat(quote.get().rate()).isEqualByComparingTo("1361.23");
        assertThat(quote.get().asOf()).isEqualTo(FALLBACK_NOW);
    }

    @Test
    void returnsEmptyOnConnectionRefusedWithoutThrowing() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        server.shutdown();   // nothing is now listening → connection refused
        server = null;       // already shut down; skip tearDown double-shutdown
        RealFxRateProviderAdapter adapter = adapterFor(baseUrl);

        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.USD);

        assertThat(quote).isEmpty();
    }

    @Test
    void returnsEmptyForBlankBaseUrl() {
        RealFxRateProviderAdapter adapter = adapterFor("  ");

        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.USD);

        assertThat(quote).isEmpty();
    }
}

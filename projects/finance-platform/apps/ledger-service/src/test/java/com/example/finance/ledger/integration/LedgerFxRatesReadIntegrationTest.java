package com.example.finance.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FX rate quote read endpoint integration tests (25th increment — TASK-FIN-BE-033,
 * ADR-002 read surface). Extends {@link AbstractLedgerIntegrationTest} (Testcontainers
 * MySQL + real Kafka + MockWebServer JWKS). Rows are seeded directly via
 * {@code jdbcTemplate} (no poller round-trip needed — pure read EP test).
 *
 * <p>The feed is enabled via {@link DynamicPropertySource} with {@code maxAgeMinutes=1440}
 * (24h default). Two {@code fx_rate_quote} rows are inserted: one fresh (as_of = now) and
 * one stale (as_of = 2 days ago).
 *
 * <p>Acceptance criteria exercised:
 * <ol>
 *   <li>(AC-1) empty cache → {@code { feedEnabled, rates: [] }} 200 (not 404).</li>
 *   <li>(AC-2) both rows returned, sorted (baseCurrency, foreignCurrency) ASC; rate
 *       exact string, source/asOf/fetchedAt present.</li>
 *   <li>(AC-3) the 2-day-old quote is {@code stale=true}; the fresh one is {@code stale=false};
 *       {@code ageSeconds} is present and reasonable.</li>
 *   <li>(AC-4) {@code feedEnabled=true} reflected from config.</li>
 *   <li>(AC-5) unauthenticated call → 401 / 403.</li>
 *   <li>(AC-6) {@code rate} serialised as a decimal string (not a float).</li>
 * </ol>
 *
 * <p>No settlement / write path invoked — pure read; no Kafka predicate conflict with
 * sibling IT classes. Uses a unique class so the {@link AbstractLedgerIntegrationTest}
 * {@code @BeforeEach} cleanup wipes the {@code fx_rate_quote} table before each test.
 */
class LedgerFxRatesReadIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String FX_RATES_PATH = "/api/finance/ledger/fx-rates";

    /** max-age = 1440 minutes = 24h (default) so a freshly inserted quote is always fresh. */
    @DynamicPropertySource
    static void fxRateFeedProperties(DynamicPropertyRegistry registry) {
        registry.add("financeplatform.ledger.fxrate.enabled", () -> "true");
        registry.add("financeplatform.ledger.fxrate.mode", () -> "noop");
        registry.add("financeplatform.ledger.fxrate.max-age-minutes", () -> "1440");
    }

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> getFxRates(String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + FX_RATES_PATH))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Insert a quote row directly via JDBC (the table has a composite PK
     * {@code (base_currency, foreign_currency)}; ON DUPLICATE KEY UPDATE is
     * idempotent — consistent with the FxRateQuote upsert path).
     */
    private void seedQuote(String base, String foreign, BigDecimal rate,
                           Instant asOf, String source, Instant fetchedAt) {
        jdbcTemplate.update(
                "INSERT INTO fx_rate_quote "
                        + "(base_currency, foreign_currency, rate, as_of, source, fetched_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "rate = VALUES(rate), as_of = VALUES(as_of), "
                        + "source = VALUES(source), fetched_at = VALUES(fetched_at)",
                base, foreign, rate,
                Timestamp.from(asOf), source, Timestamp.from(fetchedAt));
    }

    // -------------------------------------------------------------------------
    // AC-1: empty cache → 200 + rates []
    // -------------------------------------------------------------------------

    @Test
    void emptyCacheReturns200WithEmptyRates() throws Exception {
        // cleanLedgerState() in @BeforeEach already deleted all fx_rate_quote rows.
        String token = financeWriteToken();

        HttpResponse<String> resp = getFxRates(token);
        assertThat(resp.statusCode()).as("empty cache must be 200, not 404").isEqualTo(200);

        JsonNode body = objectMapper.readTree(resp.body());
        assertThat(body.at("/data/feedEnabled").asBoolean()).isTrue();
        assertThat(body.at("/data/rates").isArray()).isTrue();
        assertThat(body.at("/data/rates").size()).isZero();
        assertThat(body.at("/meta/timestamp").asText()).isNotBlank();
    }

    // -------------------------------------------------------------------------
    // AC-2 + AC-3 + AC-4 + AC-6: two rows, sorting, staleness, feedEnabled, rate string
    // -------------------------------------------------------------------------

    @Test
    void twoQuotesSortedWithStalenessAndRateAsString() throws Exception {
        Instant now = Instant.now();
        // Fresh quote: asOf = now (ageSeconds ≈ 0 < 1440 min → fresh)
        Instant freshAsOf = now;
        // Stale quote: asOf = 2 days ago (> 1440 min → stale)
        Instant staleAsOf = now.minus(java.time.Duration.ofDays(2));

        // Insert USD (fresh) and EUR (stale) — both base = KRW
        // EUR sorts before USD alphabetically → EUR should appear at index 0
        seedQuote("KRW", "USD", new BigDecimal("13.50000000"),
                freshAsOf, "stub", now);
        seedQuote("KRW", "EUR", new BigDecimal("14.20000000"),
                staleAsOf, "stub", staleAsOf);

        String token = financeWriteToken();
        HttpResponse<String> resp = getFxRates(token);
        assertThat(resp.statusCode()).isEqualTo(200);

        JsonNode body = objectMapper.readTree(resp.body());
        JsonNode data = body.get("data");

        // AC-4: feedEnabled = true (from @DynamicPropertySource)
        assertThat(data.get("feedEnabled").asBoolean()).as("AC-4 feedEnabled").isTrue();

        // Two rows present
        JsonNode rates = data.get("rates");
        assertThat(rates.size()).as("AC-2: both rows returned").isEqualTo(2);

        // AC-2: sorted (baseCurrency, foreignCurrency) ASC → EUR < USD
        JsonNode eurRow = rates.get(0);
        JsonNode usdRow = rates.get(1);
        assertThat(eurRow.get("baseCurrency").asText()).isEqualTo("KRW");
        assertThat(eurRow.get("foreignCurrency").asText()).as("AC-2: EUR sorts first").isEqualTo("EUR");
        assertThat(usdRow.get("foreignCurrency").asText()).as("AC-2: USD sorts second").isEqualTo("USD");

        // AC-6: rate is a string, not a float
        assertThat(eurRow.get("rate").asText()).as("AC-6: EUR rate as string").isEqualTo("14.20000000");
        assertThat(usdRow.get("rate").asText()).as("AC-6: USD rate as string").isEqualTo("13.50000000");

        // AC-2: other fields present
        assertThat(eurRow.get("source").asText()).isEqualTo("stub");
        assertThat(eurRow.get("asOf").asText()).isNotBlank();
        assertThat(eurRow.get("fetchedAt").asText()).isNotBlank();

        // AC-3: EUR is stale (2 days old > 1440 min); USD is fresh (asOf=now)
        assertThat(eurRow.get("stale").asBoolean()).as("AC-3: EUR stale=true").isTrue();
        assertThat(usdRow.get("stale").asBoolean()).as("AC-3: USD stale=false").isFalse();

        // AC-3: ageSeconds must be present and plausible
        long eurAge = eurRow.get("ageSeconds").asLong();
        assertThat(eurAge).as("AC-3: EUR ageSeconds ~2 days").isGreaterThanOrEqualTo(
                java.time.Duration.ofDays(2).getSeconds() - 5);
        long usdAge = usdRow.get("ageSeconds").asLong();
        assertThat(usdAge).as("AC-3: USD ageSeconds small").isLessThan(60L);
    }

    // -------------------------------------------------------------------------
    // AC-5: unauthenticated → 401/403
    // -------------------------------------------------------------------------

    @Test
    void unauthenticatedCallIsRejected() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + FX_RATES_PATH))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        // Spring Security returns 401 when no token is present
        assertThat(resp.statusCode()).as("AC-5: unauthenticated must be 401 or 403")
                .isIn(401, 403);
    }
}

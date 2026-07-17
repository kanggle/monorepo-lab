package com.example.finance.ledger.integration;

import com.example.finance.ledger.domain.journal.FxRateQuoteHistory;
import com.example.finance.ledger.domain.money.Currency;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FX rate history read endpoint integration tests (27th increment — TASK-FIN-BE-040,
 * ADR-002 history-read drill). Extends {@link AbstractLedgerIntegrationTest}
 * (Testcontainers MySQL + real Kafka + MockWebServer JWKS). History rows are seeded
 * directly via the domain repository (no poller round-trip needed — pure read EP test).
 *
 * <p>Acceptance criteria exercised:
 * <ol>
 *   <li>(AC-1) two history rows for KRW→USD → 200 with two quotes newest-first; rate is
 *       an exact string (F5).</li>
 *   <li>(AC-2) {@code ?limit=1} → only the newest quote returned.</li>
 *   <li>(AC-3) unknown / never-polled pair → 200 with empty {@code quotes} (not 404).</li>
 *   <li>(AC-4) unauthenticated → 401/403 (same {@code .authenticated()} chain).</li>
 * </ol>
 *
 * <p>Docker-gated ({@link com.example.testsupport.integration.DockerAvailableCondition}).
 * On this Windows host Docker may be unavailable (project memory
 * {@code project_testcontainers_docker_desktop_blocker}); the finance-platform
 * "Integration (Testcontainers)" CI job runs these tests on Linux where Docker is always
 * available.
 */
class LedgerFxRateHistoryIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String FX_HISTORY_PATH_USD = "/api/finance/ledger/fx-rates/USD/history";
    private static final String FX_HISTORY_PATH_XXX = "/api/finance/ledger/fx-rates/XXX/history";

    @DynamicPropertySource
    static void fxRateFeedProperties(DynamicPropertyRegistry registry) {
        registry.add("financeplatform.ledger.fxrate.enabled", () -> "true");
        registry.add("financeplatform.ledger.fxrate.mode", () -> "noop");
        registry.add("financeplatform.ledger.fxrate.max-age-minutes", () -> "1440");
    }

    @Autowired
    com.example.finance.ledger.domain.journal.repository.FxRateQuoteHistoryRepository historyRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> getHistory(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getHistory(String path, String token, int limit) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path + "?limit=" + limit))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    // -------------------------------------------------------------------------
    // AC-1: two rows, newest-first, rate as string
    // -------------------------------------------------------------------------

    @Test
    void twoHistoryRowsReturnedNewestFirstRateAsString() throws Exception {
        Instant olderFetchedAt = Instant.parse("2026-06-15T06:00:00Z");
        Instant newerFetchedAt = Instant.parse("2026-06-15T07:00:00Z");

        // Seed older row first, newer second — the endpoint must return newest first regardless.
        FxRateQuoteHistory older = FxRateQuoteHistory.of(
                Currency.KRW, Currency.USD,
                new BigDecimal("13.50000000"), olderFetchedAt, "stub", olderFetchedAt);
        FxRateQuoteHistory newer = FxRateQuoteHistory.of(
                Currency.KRW, Currency.USD,
                new BigDecimal("13.60000000"), newerFetchedAt, "stub", newerFetchedAt);
        historyRepository.append(older);
        historyRepository.append(newer);

        String token = financeWriteToken();
        HttpResponse<String> resp = getHistory(FX_HISTORY_PATH_USD, token);

        assertThat(resp.statusCode()).as("AC-1: must be 200").isEqualTo(200);

        JsonNode body = objectMapper.readTree(resp.body());
        JsonNode data = body.get("data");
        assertThat(data.get("base").asText()).isEqualTo("KRW");
        assertThat(data.get("foreign").asText()).isEqualTo("USD");

        JsonNode quotes = data.get("quotes");
        assertThat(quotes.isArray()).isTrue();
        assertThat(quotes.size()).as("AC-1: two rows").isEqualTo(2);

        // newest first
        assertThat(quotes.get(0).get("rate").asText())
                .as("AC-1: newest row rate as string (F5)").isEqualTo("13.60000000");
        assertThat(quotes.get(1).get("rate").asText())
                .as("AC-1: older row rate as string (F5)").isEqualTo("13.50000000");

        // fields present
        assertThat(quotes.get(0).get("asOf").asText()).isNotBlank();
        assertThat(quotes.get(0).get("fetchedAt").asText()).isNotBlank();
        assertThat(quotes.get(0).get("source").asText()).isEqualTo("stub");

        assertThat(body.at("/meta/timestamp").asText()).isNotBlank();
    }

    // -------------------------------------------------------------------------
    // AC-2: ?limit=1 returns newest only
    // -------------------------------------------------------------------------

    @Test
    void limitOneReturnsNewestOnly() throws Exception {
        Instant olderFetchedAt = Instant.parse("2026-06-15T06:00:00Z");
        Instant newerFetchedAt = Instant.parse("2026-06-15T07:00:00Z");

        historyRepository.append(FxRateQuoteHistory.of(
                Currency.KRW, Currency.USD,
                new BigDecimal("13.50000000"), olderFetchedAt, "stub", olderFetchedAt));
        historyRepository.append(FxRateQuoteHistory.of(
                Currency.KRW, Currency.USD,
                new BigDecimal("13.60000000"), newerFetchedAt, "stub", newerFetchedAt));

        String token = financeWriteToken();
        HttpResponse<String> resp = getHistory(FX_HISTORY_PATH_USD, token, 1);

        assertThat(resp.statusCode()).as("AC-2: must be 200").isEqualTo(200);

        JsonNode quotes = objectMapper.readTree(resp.body()).at("/data/quotes");
        assertThat(quotes.size()).as("AC-2: only one row (newest)").isEqualTo(1);
        assertThat(quotes.get(0).get("rate").asText())
                .as("AC-2: newest row returned").isEqualTo("13.60000000");
    }

    // -------------------------------------------------------------------------
    // AC-3: unknown pair → 200 empty quotes
    // -------------------------------------------------------------------------

    @Test
    void unknownPairReturns200WithEmptyQuotes() throws Exception {
        // cleanLedgerState() in @BeforeEach already cleared fx_rate_quote_history.
        String token = financeWriteToken();
        HttpResponse<String> resp = getHistory(FX_HISTORY_PATH_XXX, token);

        assertThat(resp.statusCode()).as("AC-3: unknown pair must be 200, not 404").isEqualTo(200);

        JsonNode body = objectMapper.readTree(resp.body());
        JsonNode quotes = body.at("/data/quotes");
        assertThat(quotes.isArray()).isTrue();
        assertThat(quotes.size()).as("AC-3: empty quotes for unknown pair").isZero();
    }

    // -------------------------------------------------------------------------
    // AC-4: unauthenticated → 401/403
    // -------------------------------------------------------------------------

    @Test
    void unauthenticatedCallIsRejected() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + FX_HISTORY_PATH_USD))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode())
                .as("AC-4: unauthenticated must be 401 or 403").isIn(401, 403);
    }
}

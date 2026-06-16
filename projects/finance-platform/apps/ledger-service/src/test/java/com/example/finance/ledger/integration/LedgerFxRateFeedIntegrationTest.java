package com.example.finance.ledger.integration;

import com.example.finance.ledger.application.RefreshFxRateQuotesUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * FX rate feed end-to-end integration (23rd increment, TASK-FIN-BE-031, ADR-002 — the authoritative
 * wiring gate; Docker-free {@code :check} would not catch the persisted-cache / poller-bean wiring).
 * Extended by TASK-FIN-BE-039 to assert the append-only {@code fx_rate_quote_history} trail.
 * Testcontainers MySQL (V12 + V13 run) + real Kafka + MockWebServer JWKS.
 *
 * <p>The feed gate is enabled with {@code mode=stub} via {@link DynamicPropertySource}
 * (financeplatform.ledger.fxrate.enabled=true, mode=stub, pairs=USD,EUR, stub.rates.USD=1300,
 * stub.rates.EUR=1450). The poller is invoked deterministically by autowiring
 * {@link RefreshFxRateQuotesUseCase} directly (preferred over waiting on the scheduler).
 *
 * <ol>
 *   <li><b>Cache load (AC-5)</b>: refresh → {@code fx_rate_quote} holds USD + EUR rows with
 *       rate / source="stub" / as_of / fetched_at populated;</li>
 *   <li><b>Upsert (AC-5)</b>: a second refresh keeps the row count stable (last-write-wins,
 *       fetched_at advances);</li>
 *   <li><b>History trail (TASK-FIN-BE-039)</b>: two refreshes → {@code fx_rate_quote} count = 2
 *       (one per pair), {@code fx_rate_quote_history} count = 4 (two per pair);</li>
 *   <li><b>Operator path untouched (AC-1)</b>: a manual-rate settlement on the seeded
 *       CASH_CLEARING USD position still removes the position + realizes the gain byte-identically
 *       — the loaded cache does not influence the operator path (shadow).</li>
 * </ol>
 */
class LedgerFxRateFeedIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String PROCEEDS = "SETTLEMENT_SUSPENSE";

    @Autowired
    private RefreshFxRateQuotesUseCase refreshFxRateQuotesUseCase;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void fxRateFeedProperties(DynamicPropertyRegistry registry) {
        registry.add("financeplatform.ledger.fxrate.enabled", () -> "true");
        registry.add("financeplatform.ledger.fxrate.mode", () -> "stub");
        // Poll seldom — the test drives the use case directly (deterministic); the poller bean's
        // scheduler is gated ON but its cadence is irrelevant here.
        registry.add("financeplatform.ledger.fxrate.poll-interval-ms", () -> "600000");
        registry.add("financeplatform.ledger.fxrate.initial-delay-ms", () -> "600000");
        registry.add("financeplatform.ledger.fxrate.pairs[0]", () -> "USD");
        registry.add("financeplatform.ledger.fxrate.pairs[1]", () -> "EUR");
        registry.add("financeplatform.ledger.fxrate.stub.rates.USD", () -> "1300");
        registry.add("financeplatform.ledger.fxrate.stub.rates.EUR", () -> "1450");
    }

    @Test
    void loadsTheCacheAndLeavesTheOperatorPathByteIdentical() throws Exception {
        // (1) Refresh → the cache holds USD + EUR (AC-5).
        int upserted = refreshFxRateQuotesUseCase.refresh();
        assertThat(upserted).isEqualTo(2);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT base_currency, foreign_currency, rate, source, as_of, fetched_at "
                        + "FROM fx_rate_quote ORDER BY foreign_currency");
        assertThat(rows).hasSize(2);
        Map<String, Object> eur = rows.get(0);
        Map<String, Object> usd = rows.get(1);
        assertThat(eur.get("base_currency")).isEqualTo("KRW");
        assertThat(eur.get("foreign_currency")).isEqualTo("EUR");
        assertThat(((Number) eur.get("rate")).doubleValue()).isEqualTo(1450.0);
        assertThat(eur.get("source")).isEqualTo("stub");
        assertThat(eur.get("as_of")).isNotNull();
        assertThat(eur.get("fetched_at")).isNotNull();
        assertThat(usd.get("foreign_currency")).isEqualTo("USD");
        assertThat(((Number) usd.get("rate")).doubleValue()).isEqualTo(1300.0);

        Object firstFetchedAt = usd.get("fetched_at");

        // (2) Second refresh is an upsert — row count stable, fetched_at advances (AC-5).
        // Wait a tick so the DATETIME(6) fetched_at is strictly newer (clock is systemUTC).
        await().atMost(Duration.ofSeconds(2)).until(() -> {
            refreshFxRateQuotesUseCase.refresh();
            Object now = jdbcTemplate.queryForMap(
                    "SELECT fetched_at FROM fx_rate_quote WHERE foreign_currency = 'USD'")
                    .get("fetched_at");
            return now != null && !now.equals(firstFetchedAt);
        });
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fx_rate_quote", Long.class);
        assertThat(count).isEqualTo(2L);   // upsert, not insert — still exactly the two pairs

        // (3) History trail (TASK-FIN-BE-039): two refresh() runs × two pairs = four history rows.
        // fx_rate_quote count stays at 2 (latest-only); fx_rate_quote_history grows to 4.
        Long historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fx_rate_quote_history", Long.class);
        assertThat(historyCount).isEqualTo(4L);  // 2 runs × 2 pairs = 4 append-only rows

        // USD pair specifically: fx_rate_quote = 1, fx_rate_quote_history = 2 (append-only).
        Long latestUsdCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fx_rate_quote WHERE foreign_currency = 'USD'", Long.class);
        assertThat(latestUsdCount).isEqualTo(1L);  // latest-only: still exactly one USD row
        Long historyUsdCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fx_rate_quote_history WHERE foreign_currency = 'USD'",
                Long.class);
        assertThat(historyUsdCount).isEqualTo(2L); // two runs → two history rows for USD

        // (4) Operator path is byte-identical (AC-1). A manual-rate settlement still removes the
        //     position + realizes the gain — the loaded cache does NOT influence it (shadow).
        String token = financeReadToken();
        String wallet = ensureWallet(token);
        establishUsdPosition(token, wallet);
        long[] before = usdPosition();
        assertThat(before[0]).isEqualTo(10_000L);   // USD foreign
        assertThat(before[1]).isEqualTo(130_000L);  // KRW carrying @ 13.0

        String settleKey = "FEED-SETTLE-" + newId().substring(0, 8);
        HttpResponse<String> settle = postSettlement(token, settleKey,
                "{\"ledgerAccountCode\":\"CASH_CLEARING\",\"currency\":\"USD\","
                        + "\"settlementRate\":\"13.7\",\"proceedsAccountCode\":\"" + PROCEEDS + "\","
                        + "\"reference\":\"FX-FEED-SETTLE\",\"memo\":\"liquidate USD\"}");
        assertThat(settle.statusCode()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(settle.body());
        assertThat(body.at("/data/settled").asBoolean()).isTrue();
        assertThat(body.at("/data/realizedBaseMinor").asText()).isEqualTo("7000");
        assertThat(body.at("/data/proceedsBaseMinor").asText()).isEqualTo("137000");
        assertThat(body.at("/data/outcome").asText()).isEqualTo("FX_GAIN");

        // The USD position is removed (the settlement used the OPERATOR's manual 13.7, not any
        // cached rate — net-zero / shadow).
        long[] after = usdPosition();
        assertThat(after[0]).isZero();
        assertThat(after[1]).isZero();
    }

    // ------------------------------------------------------------------------
    // Operator-path helpers (mirrored from LedgerFxSettlementIntegrationTest happy path).
    // ------------------------------------------------------------------------

    private HttpResponse<String> postEntry(String token, String idempotencyKey, String body)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/finance/ledger/entries"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        if (idempotencyKey != null) {
            b.header("Idempotency-Key", idempotencyKey);
        }
        b.POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postSettlement(String token, String idempotencyKey, String body)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/finance/ledger/settlements"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        if (idempotencyKey != null) {
            b.header("Idempotency-Key", idempotencyKey);
        }
        b.POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private long[] usdPosition() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT "
                        + "COALESCE(SUM(CASE WHEN direction='DEBIT' THEN amount_minor ELSE 0 END),0) "
                        + "- COALESCE(SUM(CASE WHEN direction='CREDIT' THEN amount_minor ELSE 0 END),0) AS f, "
                        + "COALESCE(SUM(CASE WHEN direction='DEBIT' THEN base_amount_minor ELSE 0 END),0) "
                        + "- COALESCE(SUM(CASE WHEN direction='CREDIT' THEN base_amount_minor ELSE 0 END),0) AS b "
                        + "FROM journal_line "
                        + "WHERE tenant_id='finance' AND ledger_account_code='CASH_CLEARING' "
                        + "AND currency='USD'");
        Map<String, Object> r = rows.get(0);
        return new long[]{((Number) r.get("f")).longValue(), ((Number) r.get("b")).longValue()};
    }

    private void establishUsdPosition(String token, String wallet) throws Exception {
        String posKey = "POS-" + newId().substring(0, 8);
        String posBody = "{"
                + "\"reference\":\"USD-POS-FEED\",\"memo\":\"establish USD position\","
                + "\"lines\":["
                + "{\"ledgerAccountCode\":\"CASH_CLEARING\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"10000\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"130000\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + wallet + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"130000\",\"currency\":\"KRW\"}}"
                + "]}";
        assertThat(postEntry(token, posKey, posBody).statusCode()).isEqualTo(201);
    }

    private String ensureWallet(String token) throws Exception {
        String walletAcct = "acc-" + newId();
        String wallet = "CUSTOMER_WALLET:" + walletAcct;
        publish(TOPIC_COMPLETED, walletAcct,
                completedEnvelope(newId(), newId(), walletAcct, "TOPUP", 150_000L, "KRW", null));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(ledgerAccountJpa.findByCodeAndTenantId(wallet, "finance")).isPresent());
        return wallet;
    }
}

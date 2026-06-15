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
 * FX rate feed <b>consumption</b> integration (24th increment, TASK-FIN-BE-032, ADR-002 D3/D4 —
 * the first operator-path consumer of the FIN-BE-031 cache). Sibling of
 * {@link LedgerFxRateFeedIntegrationTest} (which proves the shadow load + the manual-rate
 * net-zero). Testcontainers MySQL (V12 {@code fx_rate_quote}) + real Kafka + MockWebServer JWKS.
 *
 * <p>The feed is enabled with {@code mode=stub} (USD rate 1300) via {@link DynamicPropertySource}.
 * The cache is loaded deterministically by autowiring {@link RefreshFxRateQuotesUseCase}.
 *
 * <ol>
 *   <li><b>Omitted rate + fresh cache → fallback (AC-2)</b>: a settlement that OMITS the rate books
 *       at the cached 1300; the audit_log reason records the feed source;</li>
 *   <li><b>Omitted rate + no cache → fail-closed (AC-3)</b>: with the cache cleared, an omitted-rate
 *       settlement is 422 FX_RATE_UNAVAILABLE; nothing persists (no journal entry, the idempotency
 *       key is not consumed — a later retry with the same key still works);</li>
 *   <li><b>Manual rate → byte-identical (AC-1)</b>: a supplied rate books exactly as before.</li>
 * </ol>
 *
 * <p>Each leg uses a UNIQUE wallet/position account so the shared-Kafka outbox predicates of
 * sibling IT classes never collide.
 */
class LedgerFxRateConsumptionIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String CASH = "CASH_CLEARING";
    private static final String PROCEEDS = "SETTLEMENT_SUSPENSE";

    @Autowired
    private RefreshFxRateQuotesUseCase refreshFxRateQuotesUseCase;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void fxRateFeedProperties(DynamicPropertyRegistry registry) {
        registry.add("financeplatform.ledger.fxrate.enabled", () -> "true");
        registry.add("financeplatform.ledger.fxrate.mode", () -> "stub");
        registry.add("financeplatform.ledger.fxrate.poll-interval-ms", () -> "600000");
        registry.add("financeplatform.ledger.fxrate.initial-delay-ms", () -> "600000");
        registry.add("financeplatform.ledger.fxrate.pairs[0]", () -> "USD");
        registry.add("financeplatform.ledger.fxrate.stub.rates.USD", () -> "1300");
        // Generous staleness so the just-refreshed stub quote is fresh.
        registry.add("financeplatform.ledger.fxrate.max-age-minutes", () -> "1440");
    }

    @Test
    void omittedRateFallsBackToFreshCacheThenFailsClosedWithoutCache() throws Exception {
        String token = financeReadToken();
        String wallet = ensureWallet(token);

        // ---- (1) Omitted rate + fresh cache → fallback @ 1300, audit reason records the source. ----
        refreshFxRateQuotesUseCase.refresh(); // load USD=1300 (as_of/fetched_at = now → fresh)
        establishUsdPosition(token, wallet);   // 100 USD @ carrying 130000 (rate 1300/unit foreign)
        long[] before = usdPosition();
        assertThat(before[0]).isEqualTo(100L);       // USD foreign (minor)
        assertThat(before[1]).isEqualTo(130_000L);   // KRW carrying

        String settleKey = "CONS-OMIT-" + newId().substring(0, 8);
        HttpResponse<String> settle = postSettlement(token, settleKey,
                "{\"ledgerAccountCode\":\"CASH_CLEARING\",\"currency\":\"USD\","
                        + "\"proceedsAccountCode\":\"" + PROCEEDS + "\","
                        + "\"reference\":\"FX-CONS-OMIT\",\"memo\":\"liquidate USD via feed\"}");
        assertThat(settle.statusCode()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(settle.body());
        assertThat(body.at("/data/settled").asBoolean()).isTrue();
        // proceeds = 100 × 1300 = 130000 == carrying → realized 0 (the cached rate matched the
        // acquisition rate). The booking happened — that is the AC-2 fallback proof.
        assertThat(body.at("/data/proceedsBaseMinor").asText()).isEqualTo("130000");

        // The position is removed (settled via the cached rate).
        long[] after = usdPosition();
        assertThat(after[0]).isZero();
        assertThat(after[1]).isZero();

        // The audit reason records the feed provenance (AC-2 traceability).
        String reason = jdbcTemplate.queryForObject(
                "SELECT reason FROM audit_log WHERE tenant_id='finance' AND action='POSTED'"
                        + "ORDER BY occurred_at DESC LIMIT 1", String.class);
        assertThat(reason).contains("[fx-rate feed:stub@");

        // ---- (2) Omitted rate + NO cache → 422 FX_RATE_UNAVAILABLE; nothing persists, key free. ----
        jdbcTemplate.execute("DELETE FROM fx_rate_quote");
        String wallet2 = ensureWallet(token);
        establishUsdPosition(token, wallet2);
        long entriesBefore = countSettlementEntries();

        String failKey = "CONS-FAIL-" + newId().substring(0, 8);
        HttpResponse<String> failed = postSettlement(token, failKey,
                "{\"ledgerAccountCode\":\"CASH_CLEARING\",\"currency\":\"USD\","
                        + "\"proceedsAccountCode\":\"" + PROCEEDS + "\","
                        + "\"reference\":\"FX-CONS-FAIL\",\"memo\":\"no rate, no cache\"}");
        assertThat(failed.statusCode()).isEqualTo(422);
        assertThat(objectMapper.readTree(failed.body()).at("/code").asText())
                .isEqualTo("FX_RATE_UNAVAILABLE");
        // Nothing persisted: no new settlement entry, and the idempotency key was NOT consumed.
        assertThat(countSettlementEntries()).isEqualTo(entriesBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?", Long.class,
                "settle:" + failKey)).isZero();

        // ---- (3) Manual rate → byte-identical (AC-1). The SAME failed key now succeeds with a rate. ----
        HttpResponse<String> manual = postSettlement(token, failKey,
                "{\"ledgerAccountCode\":\"CASH_CLEARING\",\"currency\":\"USD\","
                        + "\"settlementRate\":\"1400\",\"proceedsAccountCode\":\"" + PROCEEDS + "\","
                        + "\"reference\":\"FX-CONS-MANUAL\",\"memo\":\"manual liquidation\"}");
        assertThat(manual.statusCode()).isEqualTo(201);
        JsonNode manualBody = objectMapper.readTree(manual.body());
        assertThat(manualBody.at("/data/settled").asBoolean()).isTrue();
        // proceeds = 100 × 1400 = 140000; realized = 140000 − 130000 = 10000 (gain).
        assertThat(manualBody.at("/data/proceedsBaseMinor").asText()).isEqualTo("140000");
        assertThat(manualBody.at("/data/realizedBaseMinor").asText()).isEqualTo("10000");

        // The manual-path audit reason is byte-identical to before this increment (no feed suffix).
        String manualReason = jdbcTemplate.queryForObject(
                "SELECT reason FROM audit_log WHERE tenant_id='finance' AND action='POSTED'"
                        + "ORDER BY occurred_at DESC LIMIT 1", String.class);
        assertThat(manualReason).isEqualTo("manual liquidation");
        assertThat(manualReason).doesNotContain("[fx-rate");
    }

    // ------------------------------------------------------------------------
    // Operator-path helpers (mirrored from LedgerFxRateFeedIntegrationTest).
    // ------------------------------------------------------------------------

    private long countSettlementEntries() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entry WHERE source_type = 'SETTLEMENT'", Long.class);
    }

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

    /**
     * Establish a 100-USD position @ carrying 130000 KRW (an effective acquisition rate of 1300
     * base-minor per foreign-minor — matches the stub feed rate so the omitted-rate fallback books
     * realized 0, isolating the "did it use the cache" proof from rounding).
     */
    private void establishUsdPosition(String token, String wallet) throws Exception {
        String posKey = "POS-" + newId().substring(0, 8);
        String posBody = "{"
                + "\"reference\":\"USD-POS-CONS\",\"memo\":\"establish USD position\","
                + "\"lines\":["
                + "{\"ledgerAccountCode\":\"CASH_CLEARING\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"100\",\"currency\":\"USD\"},"
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
                completedEnvelope(newId(), newId(), walletAcct, "TOPUP", 300_000L, "KRW", null));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(ledgerAccountJpa.findByCodeAndTenantId(wallet, "finance")).isPresent());
        return wallet;
    }
}

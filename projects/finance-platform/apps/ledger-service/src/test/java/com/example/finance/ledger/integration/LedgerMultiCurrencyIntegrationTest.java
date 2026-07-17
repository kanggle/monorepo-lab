package com.example.finance.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
 * Multi-currency journals end-to-end integration (8th increment, TASK-FIN-BE-014 —
 * the authoritative round-trip gate, AC-6). Testcontainers MySQL + real Kafka +
 * MockWebServer JWKS.
 *
 * <ol>
 *   <li><b>V5 backfill / net-zero</b> — drive a KRW TOPUP auto-journal; assert the
 *       persisted lines have {@code base_amount_minor == amount_minor},
 *       {@code exchange_rate == 1}, {@code base_currency == 'KRW'}, and the trial
 *       balance is in balance (AC-2);</li>
 *   <li><b>cross-currency manual entry</b> — {@code POST /entries} DR USD clearing
 *       (10000, base 135000 KRW) / CR KRW wallet (135000) → 201; read it back (per-line
 *       exchangeRate + baseAmount) + the trial balance shows the per-currency
 *       breakdown AND {@code grandBaseDebitTotal == grandBaseCreditTotal} (AC-1/AC-3);</li>
 *   <li><b>unbalanced-base manual entry</b> → 422 {@code LEDGER_ENTRY_UNBALANCED} (AC-1);</li>
 *   <li><b>cross-tenant</b> → 403 (AC-6).</li>
 * </ol>
 */
class LedgerMultiCurrencyIntegrationTest extends AbstractLedgerIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
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

    @Test
    void multiCurrencyEntryBalancesInBaseAndBackfillIsNetZero() throws Exception {
        String token = financeWriteToken();

        // (1) V5 backfill / net-zero — a KRW TOPUP auto-journal posts byte-identically.
        String walletAcct = "acc-" + newId();
        String wallet = "CUSTOMER_WALLET:" + walletAcct;
        publish(TOPIC_COMPLETED, walletAcct,
                completedEnvelope(newId(), newId(), walletAcct, "TOPUP", 150_000L, "KRW", null));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(ledgerAccountJpa.findByCodeAndTenantId(wallet, "finance")).isPresent());
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(journalLineJpa.count()).isGreaterThanOrEqualTo(2));

        // The backfilled/auto-journal KRW lines have base == amount, rate == 1, base ccy KRW.
        List<Map<String, Object>> krwLines = jdbcTemplate.queryForList(
                "SELECT amount_minor, base_amount_minor, exchange_rate, currency, base_currency "
                        + "FROM journal_line WHERE tenant_id = 'finance'");
        assertThat(krwLines).isNotEmpty();
        assertThat(krwLines).allSatisfy(row -> {
            assertThat(((Number) row.get("base_amount_minor")).longValue())
                    .isEqualTo(((Number) row.get("amount_minor")).longValue());
            assertThat(((BigDecimal) row.get("exchange_rate")).compareTo(BigDecimal.ONE)).isZero();
            assertThat(row.get("base_currency")).isEqualTo("KRW");
            assertThat(row.get("currency")).isEqualTo("KRW");
        });

        JsonNode tb0 = objectMapper.readTree(get("/api/finance/ledger/trial-balance", token).body());
        assertThat(tb0.at("/data/inBalance").asBoolean()).isTrue();

        // (2) Cross-currency manual entry — DR USD 10000 (base 135000 KRW) / CR KRW 135000.
        String key = "FX-" + newId().substring(0, 8);
        String body = "{"
                + "\"reference\":\"FX-ADJ-1\",\"memo\":\"fx adjusting entry\","
                + "\"lines\":["
                + "{\"ledgerAccountCode\":\"CASH_CLEARING\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"10000\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"135000\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + wallet + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"135000\",\"currency\":\"KRW\"}}"
                + "]}";
        HttpResponse<String> created = postEntry(token, key, body);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode createdBody = objectMapper.readTree(created.body());
        String entryId = createdBody.at("/data/entryId").asText();
        assertThat(createdBody.at("/data/balanced").asBoolean()).isTrue();

        // Read it back — the USD line carries exchangeRate "13.5" + baseAmount 135000 KRW.
        JsonNode read = objectMapper.readTree(
                get("/api/finance/ledger/entries/" + entryId, token).body());
        JsonNode lines = read.at("/data/lines");
        JsonNode usdLine = lines.get(0).get("money").get("currency").asText().equals("USD")
                ? lines.get(0) : lines.get(1);
        assertThat(usdLine.get("money").get("amount").asText()).isEqualTo("10000");
        assertThat(usdLine.get("money").get("currency").asText()).isEqualTo("USD");
        assertThat(usdLine.get("exchangeRate").asText()).isEqualTo("13.5");
        assertThat(usdLine.get("baseAmount").get("amount").asText()).isEqualTo("135000");
        assertThat(usdLine.get("baseAmount").get("currency").asText()).isEqualTo("KRW");

        // (3) Trial balance — per-currency breakdown present + base consolidated in balance.
        JsonNode tb = objectMapper.readTree(get("/api/finance/ledger/trial-balance", token).body());
        assertThat(tb.at("/data/inBalance").asBoolean()).isTrue();
        assertThat(tb.at("/data/grandBaseDebitTotal/amount").asText())
                .isEqualTo(tb.at("/data/grandBaseCreditTotal/amount").asText());
        assertThat(tb.at("/data/grandBaseDebitTotal/currency").asText()).isEqualTo("KRW");
        // CASH_CLEARING carries a USD original total + a KRW base total.
        boolean sawUsdOriginal = false;
        for (JsonNode acct : tb.at("/data/accounts")) {
            assertThat(acct.has("baseDebitTotal")).isTrue();
            assertThat(acct.has("baseCreditTotal")).isTrue();
            if (acct.get("debitTotal").get("currency").asText().equals("USD")
                    || acct.get("creditTotal").get("currency").asText().equals("USD")) {
                sawUsdOriginal = true;
            }
        }
        assertThat(sawUsdOriginal).as("trial balance keeps the USD per-currency breakdown").isTrue();

        // (4) Unbalanced-base multi-currency entry → 422 LEDGER_ENTRY_UNBALANCED.
        String unbalanced = "{\"lines\":["
                + "{\"ledgerAccountCode\":\"CASH_CLEARING\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"10000\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"135000\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + wallet + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"130000\",\"currency\":\"KRW\"}}"
                + "]}";
        HttpResponse<String> unbalancedResp =
                postEntry(token, "UNBAL-" + newId().substring(0, 8), unbalanced);
        assertThat(unbalancedResp.statusCode()).isEqualTo(422);
        assertThat(objectMapper.readTree(unbalancedResp.body()).at("/code").asText())
                .isEqualTo("LEDGER_ENTRY_UNBALANCED");

        // (5) Cross-tenant JWT → 403.
        HttpResponse<String> crossTenant =
                postEntry(crossTenantToken(), "X-" + newId().substring(0, 8), body);
        assertThat(crossTenant.statusCode()).isEqualTo(403);
    }
}

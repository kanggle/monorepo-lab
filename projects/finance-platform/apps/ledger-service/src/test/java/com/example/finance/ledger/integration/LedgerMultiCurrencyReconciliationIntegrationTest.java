package com.example.finance.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-currency reconciliation end-to-end integration (11th increment,
 * TASK-FIN-BE-017 — the authoritative round-trip gate, AC-6). Testcontainers MySQL
 * (V6 runs) + real Kafka + MockWebServer JWKS.
 *
 * <p>The FIN-BE-010 matcher matches a foreign external line to a foreign internal
 * line on the transaction (foreign) leg ({@code amount, currency, direction}). The
 * 11th increment adds the base (FX) leg: when the bank-reported base (KRW)
 * {@code baseAmount} differs from the internal line's carrying base, an
 * {@code AMOUNT_MISMATCH} discrepancy is recorded on the <b>matched</b> line.
 *
 * <p>Flow (one ordered test):
 * <ol>
 *   <li>post a multi-currency manual entry — DR USD 10000 on CASH_CLEARING
 *       (carrying 130000 KRW @ 13.0) / CR KRW wallet 130000 → a USD DEBIT internal
 *       line on CASH_CLEARING whose {@code baseMoney} is 130000 KRW (AC-3);</li>
 *   <li>ingest a USD external statement line matching the USD amount + DEBIT
 *       direction, declaring {@code baseAmount} 132000 KRW → 201: the line is
 *       MATCHED (a match exists) AND an OPEN {@code AMOUNT_MISMATCH} discrepancy
 *       (expected 130000 / actual 132000 / KRW, both refs) is recorded (AC-1);</li>
 *   <li>consume {@code finance.ledger.reconciliation.discrepancy.detected.v1} with
 *       {@code type=AMOUNT_MISMATCH} (AC-6; ledger_outbox has no topic column — a
 *       known trap, so consume via Kafka);</li>
 *   <li>a second USD line whose {@code baseAmount} == carrying → MATCHED, no
 *       discrepancy (AC-2 net-zero);</li>
 *   <li>a KRW-only statement (the FIN-BE-010 scenario) → byte-identical (AC-2);</li>
 *   <li>resolve the AMOUNT_MISMATCH discrepancy → RESOLVED (AC-5);</li>
 *   <li>cross-tenant ingest → 403 (AC-6).</li>
 * </ol>
 */
class LedgerMultiCurrencyReconciliationIntegrationTest extends AbstractLedgerIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postEntry(String token, String idempotencyKey, String body)
            throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/finance/ledger/entries"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /** Posts DR USD {@code usd} on CASH_CLEARING (base KRW) / CR KRW wallet → entryId. */
    private String postUsdClearingEntry(String token, String counterAccount, long usd, long baseKrw)
            throws Exception {
        // The counter-leg uses a SEEDED account (SETTLEMENT_SUSPENSE) — the manual
        // posting path rejects unknown accounts (no lazy mint → 404), unlike the
        // auto-journal consumer which lazily creates CUSTOMER_WALLET:* accounts. The
        // entry balances in the base currency (DR USD 10000 @ base 130000 KRW /
        // CR KRW 130000 on SETTLEMENT_SUSPENSE). Only CASH_CLEARING is reconciled, so
        // the counter line never enters the matcher's candidate set.
        String body = "{"
                + "\"reference\":\"FX-RECON\",\"memo\":\"usd clearing\","
                + "\"lines\":["
                + "{\"ledgerAccountCode\":\"CASH_CLEARING\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"" + usd + "\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"" + baseKrw + "\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + counterAccount + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"" + baseKrw + "\",\"currency\":\"KRW\"}}"
                + "]}";
        HttpResponse<String> created = postEntry(token, "FX-" + newId().substring(0, 8), body);
        assertThat(created.statusCode()).isEqualTo(201);
        return objectMapper.readTree(created.body()).at("/data/entryId").asText();
    }

    @Test
    void foreignBaseDifferenceRecordsAmountMismatchOnMatchedLine() throws Exception {
        String token = financeReadToken();

        // (1) A USD DEBIT internal line on CASH_CLEARING, carrying 130000 KRW @ 13.0.
        //     Counter-leg = the seeded SETTLEMENT_SUSPENSE (manual posting = no lazy mint).
        String entryId = postUsdClearingEntry(token, "SETTLEMENT_SUSPENSE", 10_000L, 130_000L);

        // (AC-3) the persisted internal CASH_CLEARING USD line carries the KRW base
        // (130000), not its USD amount (10000) — findUnmatchedInternalLines reads this.
        List<Map<String, Object>> clearingLines = jdbcTemplate.queryForList(
                "SELECT amount_minor, base_amount_minor, currency, base_currency "
                        + "FROM journal_line WHERE tenant_id = 'finance' "
                        + "AND ledger_account_code = 'CASH_CLEARING'");
        assertThat(clearingLines).hasSize(1);
        Map<String, Object> usdLine = clearingLines.get(0);
        assertThat(((Number) usdLine.get("amount_minor")).longValue()).isEqualTo(10_000L);
        assertThat(usdLine.get("currency")).isEqualTo("USD");
        assertThat(((Number) usdLine.get("base_amount_minor")).longValue()).isEqualTo(130_000L);
        assertThat(usdLine.get("base_currency")).isEqualTo("KRW");

        // (2) Ingest a USD external line matching amount + DEBIT direction, base 132000.
        String ingestBody = """
                { "ledgerAccountCode": "CASH_CLEARING", "source": "BANK",
                  "statementDate": "2026-01-31",
                  "lines": [
                    { "externalRef": "FXTXN-001",
                      "money": { "amount": "10000", "currency": "USD" },
                      "baseAmount": { "amount": "132000", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "2026-01-20",
                      "description": "USD settlement @ bank rate" } ] }
                """;
        HttpResponse<String> ingestResp =
                post("/api/finance/ledger/reconciliation/statements", token, ingestBody);
        assertThat(ingestResp.statusCode()).isEqualTo(201);
        JsonNode data = objectMapper.readTree(ingestResp.body()).get("data");
        String statementId = data.get("statementId").asText();

        // (AC-1) the line is MATCHED (transaction leg) AND carries an AMOUNT_MISMATCH.
        assertThat(data.get("matchedCount").asInt()).isEqualTo(1);
        assertThat(data.get("matches").get(0).get("journalEntryId").asText()).isEqualTo(entryId);
        assertThat(data.get("matches").get(0).get("money").get("currency").asText()).isEqualTo("USD");
        assertThat(data.get("discrepancyCount").asInt()).isEqualTo(1);
        JsonNode disc = data.get("discrepancies").get(0);
        assertThat(disc.get("type").asText()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(disc.get("status").asText()).isEqualTo("OPEN");
        assertThat(disc.get("externalRef").asText()).isEqualTo("FXTXN-001");
        assertThat(disc.get("journalEntryId").asText()).isEqualTo(entryId);
        assertThat(disc.get("expectedMinor").asText()).isEqualTo("130000"); // internal carrying base
        assertThat(disc.get("actualMinor").asText()).isEqualTo("132000");   // bank-reported base
        assertThat(disc.get("currency").asText()).isEqualTo("KRW");

        // (3) Consume the discrepancy.detected feed with type=AMOUNT_MISMATCH (AC-6).
        JsonNode detectedEnv = awaitEnvelope(TOPIC_DISCREPANCY_DETECTED,
                env -> env.path("payload").path("ledgerAccountCode").asText().equals("CASH_CLEARING")
                        && env.path("payload").path("type").asText().equals("AMOUNT_MISMATCH"),
                Duration.ofSeconds(30));
        assertThat(detectedEnv.get("aggregateType").asText()).isEqualTo("ReconciliationDiscrepancy");
        JsonNode payload = detectedEnv.get("payload");
        // F5 — money minor amounts are STRINGs, never floats; currency is KRW (base leg).
        assertThat(payload.get("expectedMinor").isTextual()).isTrue();
        assertThat(payload.get("actualMinor").isTextual()).isTrue();
        assertThat(payload.get("expectedMinor").asText()).isEqualTo("130000");
        assertThat(payload.get("actualMinor").asText()).isEqualTo("132000");
        assertThat(payload.get("currency").asText()).isEqualTo("KRW");

        // (AC-5) resolve the AMOUNT_MISMATCH → RESOLVED (operator-only; no auto-close).
        String discrepancyId = disc.get("discrepancyId").asText();
        HttpResponse<String> resolveResp = post(
                "/api/finance/ledger/reconciliation/discrepancies/" + discrepancyId + "/resolve",
                token, "{\"resolutionType\":\"ACCEPTED\",\"note\":\"fx diff, operator booked\"}");
        assertThat(resolveResp.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(resolveResp.body()).at("/data/status").asText())
                .isEqualTo("RESOLVED");

        // (4) AC-2 net-zero — a USD line whose external base EQUALS the carrying base
        //     → MATCHED, no discrepancy. Fresh ledger state + fresh internal line.
        cleanLedgerState();
        postUsdClearingEntry(token, "SETTLEMENT_SUSPENSE", 10_000L, 130_000L);
        String equalBaseBody = """
                { "ledgerAccountCode": "CASH_CLEARING", "source": "BANK",
                  "statementDate": "2026-01-31",
                  "lines": [
                    { "externalRef": "FXTXN-002",
                      "money": { "amount": "10000", "currency": "USD" },
                      "baseAmount": { "amount": "130000", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "2026-01-20" } ] }
                """;
        JsonNode equalData = objectMapper.readTree(
                post("/api/finance/ledger/reconciliation/statements", token, equalBaseBody).body())
                .get("data");
        assertThat(equalData.get("matchedCount").asInt()).isEqualTo(1);
        assertThat(equalData.get("discrepancyCount").asInt()).isZero();

        // (5) AC-2 net-zero — a KRW-only statement (the FIN-BE-010 scenario): one
        //     matching DR + one non-matching → 1 match + 1 UNMATCHED_EXTERNAL (no
        //     base-leg noise on a KRW line).
        cleanLedgerState();
        String walletKrw = "acc-" + newId();
        publish(TOPIC_COMPLETED, walletKrw,
                completedEnvelope(newId(), newId(), walletKrw, "TOPUP", 150_000L, "KRW", null));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(journalLineJpa.count()).isGreaterThanOrEqualTo(2));
        String krwBody = """
                { "ledgerAccountCode": "CASH_CLEARING", "source": "BANK",
                  "statementDate": "2026-01-31",
                  "lines": [
                    { "externalRef": "BANKTXN-001",
                      "money": { "amount": "150000", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "2026-01-15" },
                    { "externalRef": "BANKTXN-002",
                      "money": { "amount": "70000", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "2026-01-16" } ] }
                """;
        JsonNode krwData = objectMapper.readTree(
                post("/api/finance/ledger/reconciliation/statements", token, krwBody).body())
                .get("data");
        assertThat(krwData.get("matchedCount").asInt()).isEqualTo(1);
        assertThat(krwData.get("discrepancyCount").asInt()).isEqualTo(1);
        assertThat(krwData.get("discrepancies").get(0).get("type").asText())
                .isEqualTo("UNMATCHED_EXTERNAL");

        // (6) AC-6 — cross-tenant JWT → 403.
        HttpResponse<String> crossTenant =
                post("/api/finance/ledger/reconciliation/statements", crossTenantToken(), krwBody);
        assertThat(crossTenant.statusCode()).isEqualTo(403);

        // statementId from the first ingest is retained for provenance (referenced).
        assertThat(statementId).isNotBlank();
    }
}

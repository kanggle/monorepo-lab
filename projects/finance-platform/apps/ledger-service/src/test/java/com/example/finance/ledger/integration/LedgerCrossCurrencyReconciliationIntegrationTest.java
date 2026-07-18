package com.example.finance.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-currency base-leg matching end-to-end integration (14th increment,
 * TASK-FIN-BE-021 — the authoritative round-trip gate). Testcontainers MySQL (V8
 * runs) + real Kafka + MockWebServer JWKS.
 *
 * <p>A bank settles a foreign position <b>in the base currency (KRW)</b> while the
 * ledger booked the underlying as a <b>foreign (USD)</b> line carrying a KRW base.
 * Under the same-currency matcher that KRW external would be {@code UNMATCHED_EXTERNAL}
 * and the foreign internal {@code UNMATCHED_INTERNAL}. The 14th increment pairs them
 * by carrying base (within the per-tenant FxTolerance, after same-currency matching),
 * recording a match flagged {@code crossCurrency=true} with NO discrepancy.
 *
 * <p>This IT reconciles <b>SETTLEMENT_SUSPENSE</b> (counter-leg CASH_CLEARING) — a
 * DISTINCT reconciled account from the FIN-BE-017 / FIN-BE-020 ITs (which reconcile
 * CASH_CLEARING), so the shared-Kafka cross-class predicate (by ledgerAccountCode) of
 * any sibling IT never collides with this one (FIN-BE-020 trap).
 *
 * <p>Flow (one ordered test):
 * <ol>
 *   <li>post a USD DEBIT internal line on SETTLEMENT_SUSPENSE carrying 130000 KRW;</li>
 *   <li>ingest a KRW external line (130000, DEBIT) with NO same-currency candidate →
 *       a cross-currency match (crossCurrency=true), 0 discrepancies; assert the
 *       persisted {@code reconciliation_match.cross_currency = 1};</li>
 *   <li>a KRW external whose amount has no carrying-base match → UNMATCHED_EXTERNAL;</li>
 *   <li>within a configured FxTolerance → a cross-currency match;</li>
 *   <li>cross-tenant ingest → 403.</li>
 * </ol>
 */
class LedgerCrossCurrencyReconciliationIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String RECON_ACCOUNT = "SETTLEMENT_SUSPENSE";
    private static final String COUNTER_ACCOUNT = "CASH_CLEARING";

    private final HttpClient http = HttpClient.newHttpClient();

    protected HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String token, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
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

    /** Posts DR USD {@code usd} on SETTLEMENT_SUSPENSE (base KRW) / CR KRW on CASH_CLEARING. */
    private String postUsdSuspenseEntry(String token, long usd, long baseKrw) throws Exception {
        String body = "{"
                + "\"reference\":\"XCUR-RECON\",\"memo\":\"usd suspense\","
                + "\"lines\":["
                + "{\"ledgerAccountCode\":\"" + RECON_ACCOUNT + "\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"" + usd + "\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"" + baseKrw + "\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + COUNTER_ACCOUNT + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"" + baseKrw + "\",\"currency\":\"KRW\"}}"
                + "]}";
        HttpResponse<String> created = postEntry(token, "XCUR-" + newId().substring(0, 8), body);
        assertThat(created.statusCode()).isEqualTo(201);
        return objectMapper.readTree(created.body()).at("/data/entryId").asText();
    }

    @Test
    void krwExternalMatchesForeignInternalByCarryingBase() throws Exception {
        String token = financeWriteToken();

        // (1) A USD DEBIT internal line on SETTLEMENT_SUSPENSE carrying 130000 KRW @ 13.0.
        String entryId = postUsdSuspenseEntry(token, 10_000L, 130_000L);

        // The persisted internal SETTLEMENT_SUSPENSE USD line carries the KRW base (130000).
        List<Map<String, Object>> suspenseLines = jdbcTemplate.queryForList(
                "SELECT amount_minor, base_amount_minor, currency, base_currency "
                        + "FROM journal_line WHERE tenant_id = 'finance' "
                        + "AND ledger_account_code = '" + RECON_ACCOUNT + "'");
        assertThat(suspenseLines).hasSize(1);
        Map<String, Object> usdLine = suspenseLines.get(0);
        assertThat(((Number) usdLine.get("amount_minor")).longValue()).isEqualTo(10_000L);
        assertThat(usdLine.get("currency")).isEqualTo("USD");
        assertThat(((Number) usdLine.get("base_amount_minor")).longValue()).isEqualTo(130_000L);
        assertThat(usdLine.get("base_currency")).isEqualTo("KRW");

        // (2) Ingest a KRW external line (130000, DEBIT) — NO same-currency candidate (the
        //     internal is USD). The carrying base (130000 KRW) matches the external KRW
        //     amount → a cross-currency match, 0 discrepancies.
        String ingestBody = """
                { "ledgerAccountCode": "SETTLEMENT_SUSPENSE", "source": "BANK",
                  "statementDate": "2026-01-31",
                  "lines": [
                    { "externalRef": "XCURTXN-001",
                      "money": { "amount": "130000", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "2026-01-20",
                      "description": "USD position settled in KRW by the bank" } ] }
                """;
        HttpResponse<String> ingestResp =
                post("/api/finance/ledger/reconciliation/statements", token, ingestBody);
        assertThat(ingestResp.statusCode()).isEqualTo(201);
        JsonNode data = objectMapper.readTree(ingestResp.body()).get("data");
        String statementId = data.get("statementId").asText();

        // The KRW external is MATCHED cross-currency, 0 discrepancies; the match carries
        // the KRW money + the foreign internal's journalEntryId + crossCurrency=true.
        assertThat(data.get("matchedCount").asInt()).isEqualTo(1);
        assertThat(data.get("discrepancyCount").asInt()).isZero();
        JsonNode match = data.get("matches").get(0);
        assertThat(match.get("journalEntryId").asText()).isEqualTo(entryId);
        assertThat(match.get("money").get("currency").asText()).isEqualTo("KRW");
        assertThat(match.get("money").get("amount").asText()).isEqualTo("130000");
        assertThat(match.get("crossCurrency").asBoolean()).isTrue();

        // The persisted reconciliation_match row carries cross_currency = 1 (audit flag).
        List<Map<String, Object>> matchRows = jdbcTemplate.queryForList(
                "SELECT journal_entry_id, amount_minor, currency, cross_currency "
                        + "FROM reconciliation_match WHERE tenant_id = 'finance' "
                        + "AND ledger_account_code = '" + RECON_ACCOUNT + "'");
        assertThat(matchRows).hasSize(1);
        Map<String, Object> matchRow = matchRows.get(0);
        assertThat(matchRow.get("journal_entry_id")).isEqualTo(entryId);
        assertThat(((Number) matchRow.get("amount_minor")).longValue()).isEqualTo(130_000L);
        assertThat(matchRow.get("currency")).isEqualTo("KRW");
        // MySQL BOOLEAN (TINYINT(1)) surfaces via JDBC as a Boolean → cross_currency = true.
        assertThat(matchRow.get("cross_currency")).isEqualTo(Boolean.TRUE);

        // (3) A KRW external with no carrying-base match (under EXACT, 1 unit off) →
        //     UNMATCHED_EXTERNAL + the foreign internal → UNMATCHED_INTERNAL (no match).
        cleanLedgerState();
        postUsdSuspenseEntry(token, 10_000L, 130_000L);
        String noMatchBody = """
                { "ledgerAccountCode": "SETTLEMENT_SUSPENSE", "source": "BANK",
                  "statementDate": "2026-01-31",
                  "lines": [
                    { "externalRef": "XCURTXN-002",
                      "money": { "amount": "130001", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "2026-01-20" } ] }
                """;
        JsonNode noMatchData = objectMapper.readTree(
                post("/api/finance/ledger/reconciliation/statements", token, noMatchBody).body())
                .get("data");
        assertThat(noMatchData.get("matchedCount").asInt()).isZero();
        assertThat(noMatchData.get("discrepancyCount").asInt()).isEqualTo(2);
        assertThat(noMatchData.get("discrepancies")).extracting(d -> d.get("type").asText())
                .containsExactlyInAnyOrder("UNMATCHED_EXTERNAL", "UNMATCHED_INTERNAL");

        // (4) A configured FxTolerance (100 bps) → a within-tolerance KRW external
        //     (131200 vs carrying 130000, diff 1200 <= 1300 band) cross-matches cleanly.
        cleanLedgerState();
        HttpResponse<String> tolSet = put("/api/finance/ledger/reconciliation/fx-tolerance", token,
                "{\"toleranceBps\":100,\"floorMinor\":0}");
        assertThat(tolSet.statusCode()).isEqualTo(200);
        postUsdSuspenseEntry(token, 10_000L, 130_000L);
        String withinTolBody = """
                { "ledgerAccountCode": "SETTLEMENT_SUSPENSE", "source": "BANK",
                  "statementDate": "2026-01-31",
                  "lines": [
                    { "externalRef": "XCURTXN-003",
                      "money": { "amount": "131200", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "2026-01-20" } ] }
                """;
        JsonNode tolData = objectMapper.readTree(
                post("/api/finance/ledger/reconciliation/statements", token, withinTolBody).body())
                .get("data");
        assertThat(tolData.get("matchedCount").asInt()).isEqualTo(1);
        assertThat(tolData.get("discrepancyCount").asInt()).isZero();
        assertThat(tolData.get("matches").get(0).get("crossCurrency").asBoolean()).isTrue();

        // (5) cross-tenant JWT → 403.
        HttpResponse<String> crossTenant =
                post("/api/finance/ledger/reconciliation/statements", crossTenantToken(), ingestBody);
        assertThat(crossTenant.statusCode()).isEqualTo(403);

        assertThat(statementId).isNotBlank();
    }
}

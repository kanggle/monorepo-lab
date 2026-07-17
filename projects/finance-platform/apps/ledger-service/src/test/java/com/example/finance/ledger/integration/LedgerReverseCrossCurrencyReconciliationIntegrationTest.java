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
 * Reverse cross-currency base-leg matching end-to-end integration (19th increment,
 * TASK-FIN-BE-027 — the authoritative round-trip gate; the MIRROR of FIN-BE-021).
 * Testcontainers MySQL + real Kafka + MockWebServer JWKS.
 *
 * <p>A bank frequently settles a position <b>in a foreign currency (USD)</b> — reporting
 * the bank-side base (KRW) value — while the ledger booked the settlement as a
 * <b>base-currency (KRW)</b> internal line. Under the same-currency matcher that foreign
 * external would be {@code UNMATCHED_EXTERNAL} and the KRW internal {@code UNMATCHED_INTERNAL}.
 * The 19th increment pairs them by the external's bank-reported base vs the internal KRW
 * amount (within the per-tenant FxTolerance, after same-currency matching), recording a
 * match flagged {@code crossCurrency=true} with NO discrepancy.
 *
 * <p>This IT reconciles <b>SETTLEMENT_SUSPENSE</b> (counter-leg CASH_CLEARING) — the only
 * reconcilable clearing accounts are CASH_CLEARING / SETTLEMENT_SUSPENSE (the
 * {@code ReconciliationAccounts} allow-list; a custom code → 422
 * {@code RECONCILIATION_ACCOUNT_INVALID}). The {@code @BeforeEach cleanLedgerState()} wipes
 * ALL transactional tables (including {@code reconciliation_match}) before every method, so
 * there is no cross-class persistence collision with the FIN-BE-021 IT (which also reconciles
 * SETTLEMENT_SUSPENSE); this IT also drains no Kafka envelope, so the shared-Kafka cross-class
 * predicate (FIN-BE-020 trap) never applies. Distinct externalRefs (REVXCURTXN-*) keep its
 * statement lines self-identifying.
 *
 * <p>Flow (one ordered test):
 * <ol>
 *   <li>post a KRW DEBIT internal line (130000) on SETTLEMENT_SUSPENSE;</li>
 *   <li>ingest a USD external line (10000) carrying a KRW baseAmount (130000) with NO
 *       same-currency candidate → a reverse cross-currency match (crossCurrency=true), 0
 *       discrepancies; assert the persisted {@code reconciliation_match.cross_currency = 1};</li>
 *   <li>a USD external whose base has no KRW-amount match → UNMATCHED_EXTERNAL;</li>
 *   <li>a USD external WITHOUT a baseAmount → UNMATCHED_EXTERNAL (no match key);</li>
 *   <li>cross-tenant ingest → 403.</li>
 * </ol>
 */
class LedgerReverseCrossCurrencyReconciliationIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String RECON_ACCOUNT = "SETTLEMENT_SUSPENSE";
    private static final String COUNTER_ACCOUNT = "CASH_CLEARING";

    private final HttpClient http = HttpClient.newHttpClient();

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

    /** Posts DR KRW {@code krw} on SETTLEMENT_SUSPENSE / CR KRW on CASH_CLEARING. */
    private String postKrwClearingEntry(String token, long krw) throws Exception {
        String body = "{"
                + "\"reference\":\"REVXCUR-RECON\",\"memo\":\"krw clearing\","
                + "\"lines\":["
                + "{\"ledgerAccountCode\":\"" + RECON_ACCOUNT + "\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"" + krw + "\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + COUNTER_ACCOUNT + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"" + krw + "\",\"currency\":\"KRW\"}}"
                + "]}";
        HttpResponse<String> created = postEntry(token, "REVXCUR-" + newId().substring(0, 8), body);
        assertThat(created.statusCode()).isEqualTo(201);
        return objectMapper.readTree(created.body()).at("/data/entryId").asText();
    }

    @Test
    void foreignExternalMatchesKrwInternalByDeclaredBase() throws Exception {
        String token = financeWriteToken();

        // (1) A KRW DEBIT internal line (130000) on FX_REVCROSS_KRW_CLEARING.
        String entryId = postKrwClearingEntry(token, 130_000L);

        List<Map<String, Object>> clearingLines = jdbcTemplate.queryForList(
                "SELECT amount_minor, currency FROM journal_line WHERE tenant_id = 'finance' "
                        + "AND ledger_account_code = '" + RECON_ACCOUNT + "'");
        assertThat(clearingLines).hasSize(1);
        assertThat(((Number) clearingLines.get(0).get("amount_minor")).longValue()).isEqualTo(130_000L);
        assertThat(clearingLines.get(0).get("currency")).isEqualTo("KRW");

        // (2) Ingest a USD external line (10000) carrying a KRW baseAmount (130000) — NO
        //     same-currency candidate (the internal is KRW; the external is USD). The
        //     declared base (130000 KRW) matches the internal KRW amount → a reverse
        //     cross-currency match, 0 discrepancies.
        String ingestBody = """
                { "ledgerAccountCode": "SETTLEMENT_SUSPENSE", "source": "BANK",
                  "statementDate": "2026-01-31",
                  "lines": [
                    { "externalRef": "REVXCURTXN-001",
                      "money": { "amount": "10000", "currency": "USD" },
                      "baseAmount": { "amount": "130000", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "2026-01-20",
                      "description": "KRW position settled in USD by the bank" } ] }
                """;
        HttpResponse<String> ingestResp =
                post("/api/finance/ledger/reconciliation/statements", token, ingestBody);
        assertThat(ingestResp.statusCode()).isEqualTo(201);
        JsonNode data = objectMapper.readTree(ingestResp.body()).get("data");
        String statementId = data.get("statementId").asText();

        // The USD external is MATCHED reverse-cross-currency, 0 discrepancies; the match
        // carries the USD money + the KRW internal's journalEntryId + crossCurrency=true.
        assertThat(data.get("matchedCount").asInt()).isEqualTo(1);
        assertThat(data.get("discrepancyCount").asInt()).isZero();
        JsonNode match = data.get("matches").get(0);
        assertThat(match.get("journalEntryId").asText()).isEqualTo(entryId);
        assertThat(match.get("money").get("currency").asText()).isEqualTo("USD");
        assertThat(match.get("money").get("amount").asText()).isEqualTo("10000");
        assertThat(match.get("crossCurrency").asBoolean()).isTrue();

        // The persisted reconciliation_match row carries cross_currency = 1 (audit flag).
        List<Map<String, Object>> matchRows = jdbcTemplate.queryForList(
                "SELECT journal_entry_id, amount_minor, currency, cross_currency "
                        + "FROM reconciliation_match WHERE tenant_id = 'finance' "
                        + "AND ledger_account_code = '" + RECON_ACCOUNT + "'");
        assertThat(matchRows).hasSize(1);
        Map<String, Object> matchRow = matchRows.get(0);
        assertThat(matchRow.get("journal_entry_id")).isEqualTo(entryId);
        assertThat(((Number) matchRow.get("amount_minor")).longValue()).isEqualTo(10_000L);
        assertThat(matchRow.get("currency")).isEqualTo("USD");
        assertThat(matchRow.get("cross_currency")).isEqualTo(Boolean.TRUE);

        // (3) A USD external whose declared base has no KRW-amount match (under EXACT, 1 unit
        //     off) → UNMATCHED_EXTERNAL + the KRW internal → UNMATCHED_INTERNAL (no match).
        cleanLedgerState();
        postKrwClearingEntry(token, 130_000L);
        String noMatchBody = """
                { "ledgerAccountCode": "SETTLEMENT_SUSPENSE", "source": "BANK",
                  "statementDate": "2026-01-31",
                  "lines": [
                    { "externalRef": "REVXCURTXN-002",
                      "money": { "amount": "10000", "currency": "USD" },
                      "baseAmount": { "amount": "130001", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "2026-01-20" } ] }
                """;
        JsonNode noMatchData = objectMapper.readTree(
                post("/api/finance/ledger/reconciliation/statements", token, noMatchBody).body())
                .get("data");
        assertThat(noMatchData.get("matchedCount").asInt()).isZero();
        assertThat(noMatchData.get("discrepancyCount").asInt()).isEqualTo(2);
        assertThat(noMatchData.get("discrepancies")).extracting(d -> d.get("type").asText())
                .containsExactlyInAnyOrder("UNMATCHED_EXTERNAL", "UNMATCHED_INTERNAL");

        // (4) A USD external WITHOUT a baseAmount → no reverse match key → UNMATCHED_EXTERNAL
        //     + the KRW internal → UNMATCHED_INTERNAL (the reverse pass is never entered).
        cleanLedgerState();
        postKrwClearingEntry(token, 130_000L);
        String noBaseBody = """
                { "ledgerAccountCode": "SETTLEMENT_SUSPENSE", "source": "BANK",
                  "statementDate": "2026-01-31",
                  "lines": [
                    { "externalRef": "REVXCURTXN-003",
                      "money": { "amount": "10000", "currency": "USD" },
                      "direction": "DEBIT", "valueDate": "2026-01-20" } ] }
                """;
        JsonNode noBaseData = objectMapper.readTree(
                post("/api/finance/ledger/reconciliation/statements", token, noBaseBody).body())
                .get("data");
        assertThat(noBaseData.get("matchedCount").asInt()).isZero();
        assertThat(noBaseData.get("discrepancyCount").asInt()).isEqualTo(2);
        assertThat(noBaseData.get("discrepancies")).extracting(d -> d.get("type").asText())
                .containsExactlyInAnyOrder("UNMATCHED_EXTERNAL", "UNMATCHED_INTERNAL");

        // (5) cross-tenant JWT → 403.
        HttpResponse<String> crossTenant =
                post("/api/finance/ledger/reconciliation/statements", crossTenantToken(), ingestBody);
        assertThat(crossTenant.statusCode()).isEqualTo(403);

        assertThat(statementId).isNotBlank();
    }
}

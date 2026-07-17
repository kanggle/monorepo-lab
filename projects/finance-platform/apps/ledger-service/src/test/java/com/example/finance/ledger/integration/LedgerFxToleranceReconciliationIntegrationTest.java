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
 * Configurable FX reconciliation tolerance end-to-end integration (13th increment,
 * TASK-FIN-BE-020 — the authoritative wiring gate; Docker-free {@code :check} would
 * not catch persisted-config resolve / audit-column population). Testcontainers MySQL
 * (V7 runs) + real Kafka + MockWebServer JWKS.
 *
 * <p><b>Account isolation.</b> This IT reconciles {@code SETTLEMENT_SUSPENSE} (a
 * reconcilable clearing account) — NOT {@code CASH_CLEARING}. The shared static Kafka
 * container retains {@code discrepancy.detected} events across IT classes, and a sibling
 * earliest-reading consumer (the FIN-BE-017 IT) filters its expected envelope by
 * {@code ledgerAccountCode == CASH_CLEARING}; emitting this IT's AMOUNT_MISMATCH events
 * on a different account dodges that predicate so neither class poisons the other.
 *
 * <p>Scenarios (one ordered test):
 * <ol>
 *   <li><b>absent → EXACT</b>: with no config row, a within-band-looking base diff
 *       still records {@code AMOUNT_MISMATCH} (byte-identical to FIN-BE-017);</li>
 *   <li><b>GET default</b>: unset → {@code { toleranceBps: 0, floorMinor: 0 }};</li>
 *   <li><b>PUT + GET round-trip + audit</b>: set bps/floor → 200; GET reflects it +
 *       the {@code updatedBy} (actor) / {@code updatedAt} audit columns populated;</li>
 *   <li><b>within tolerance → 0 AMOUNT_MISMATCH + match recorded</b>: with the config
 *       set, a within-band base diff matches cleanly (the txn match is STILL
 *       recorded);</li>
 *   <li><b>exceeds tolerance → AMOUNT_MISMATCH</b>: a base diff above the band still
 *       raises the discrepancy;</li>
 *   <li><b>negative → VALIDATION_ERROR</b>: a negative bps PUT → 400.</li>
 * </ol>
 */
class LedgerFxToleranceReconciliationIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String FX_TOLERANCE_PATH =
            "/api/finance/ledger/reconciliation/fx-tolerance";

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET().build();
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

    /**
     * Posts DR USD 10000 on the reconciled SETTLEMENT_SUSPENSE account carrying
     * {@code baseKrw} / CR KRW on CASH_CLEARING (the counter-leg; only
     * SETTLEMENT_SUSPENSE is reconciled here, so the counter never enters the matcher).
     */
    private void postUsdClearingEntry(String token, long baseKrw) throws Exception {
        String body = "{"
                + "\"reference\":\"FX-TOL\",\"memo\":\"usd clearing\","
                + "\"lines\":["
                + "{\"ledgerAccountCode\":\"SETTLEMENT_SUSPENSE\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"10000\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"" + baseKrw + "\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"CASH_CLEARING\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"" + baseKrw + "\",\"currency\":\"KRW\"}}"
                + "]}";
        HttpResponse<String> created = postEntry(token, "FXT-" + newId().substring(0, 8), body);
        assertThat(created.statusCode()).isEqualTo(201);
    }

    /** Ingests a USD line matching the internal USD amount with the given external base. */
    private JsonNode ingestUsd(String token, String externalRef, long externalBaseKrw)
            throws Exception {
        String body = """
                { "ledgerAccountCode": "SETTLEMENT_SUSPENSE", "source": "BANK",
                  "statementDate": "2026-01-31",
                  "lines": [
                    { "externalRef": "%s",
                      "money": { "amount": "10000", "currency": "USD" },
                      "baseAmount": { "amount": "%d", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "2026-01-20" } ] }
                """.formatted(externalRef, externalBaseKrw);
        HttpResponse<String> resp = post(
                "/api/finance/ledger/reconciliation/statements", token, body);
        assertThat(resp.statusCode()).isEqualTo(201);
        return objectMapper.readTree(resp.body()).get("data");
    }

    @Test
    void configurableToleranceSuppressesWithinBandAmountMismatch() throws Exception {
        String token = financeWriteToken();

        // (1) ABSENT → EXACT: no config row; internal carrying 130000, bank reports
        //     130500 (a 500 diff). Under EXACT this still records AMOUNT_MISMATCH —
        //     byte-identical to FIN-BE-017.
        postUsdClearingEntry(token, 130_000L);
        JsonNode exactData = ingestUsd(token, "FXT-EXACT", 130_500L);
        assertThat(exactData.get("matchedCount").asInt()).isEqualTo(1);
        assertThat(exactData.get("discrepancyCount").asInt()).isEqualTo(1);
        assertThat(exactData.get("discrepancies").get(0).get("type").asText())
                .isEqualTo("AMOUNT_MISMATCH");

        // (2) GET default — unset → EXACT {0,0}.
        HttpResponse<String> getDefault = get(FX_TOLERANCE_PATH, token);
        assertThat(getDefault.statusCode()).isEqualTo(200);
        JsonNode defData = objectMapper.readTree(getDefault.body()).get("data");
        assertThat(defData.get("toleranceBps").asInt()).isZero();
        assertThat(defData.get("floorMinor").asLong()).isZero();
        assertThat(defData.has("updatedBy")).isFalse();

        // (3) PUT — set 100 bps (1%) + a 200 floor; round-trip + audit columns.
        HttpResponse<String> putResp = put(FX_TOLERANCE_PATH, token,
                "{\"toleranceBps\":100,\"floorMinor\":200}");
        assertThat(putResp.statusCode()).isEqualTo(200);
        JsonNode putData = objectMapper.readTree(putResp.body()).get("data");
        assertThat(putData.get("toleranceBps").asInt()).isEqualTo(100);
        assertThat(putData.get("floorMinor").asLong()).isEqualTo(200L);
        assertThat(putData.get("updatedBy").asText()).isEqualTo("user-1"); // JWT subject

        HttpResponse<String> getAfter = get(FX_TOLERANCE_PATH, token);
        JsonNode afterData = objectMapper.readTree(getAfter.body()).get("data");
        assertThat(afterData.get("toleranceBps").asInt()).isEqualTo(100);
        assertThat(afterData.get("floorMinor").asLong()).isEqualTo(200L);

        // Audit columns persisted (updated_by = actor, updated_at not null).
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT updated_by, updated_at FROM reconciliation_fx_tolerance "
                        + "WHERE tenant_id = 'finance'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("updated_by")).isEqualTo("user-1");
        assertThat(rows.get(0).get("updated_at")).isNotNull();

        // An audit_log row records the config mutation.
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE tenant_id = 'finance' "
                        + "AND action = 'FX_TOLERANCE_SET' AND actor = 'user-1'", Integer.class);
        assertThat(auditCount).isEqualTo(1);

        // (4) WITHIN tolerance → 0 AMOUNT_MISMATCH + the transaction match STILL recorded.
        //     100 bps of 130000 = 1300 band; a 500 diff is within → clean match (F8: the
        //     settlement is still identified; tolerance suppresses only the base discrepancy).
        cleanLedgerStateButKeepTolerance(token, 100, 200L);
        postUsdClearingEntry(token, 130_000L);
        JsonNode withinData = ingestUsd(token, "FXT-WITHIN", 130_500L); // diff 500 <= 1300
        assertThat(withinData.get("matchedCount").asInt()).isEqualTo(1);
        assertThat(withinData.get("matches").get(0).get("money").get("currency").asText())
                .isEqualTo("USD");
        assertThat(withinData.get("discrepancyCount").asInt()).isZero();

        // (5) EXCEEDS tolerance → AMOUNT_MISMATCH still raised. A 2000 diff > 1300 band.
        cleanLedgerStateButKeepTolerance(token, 100, 200L);
        postUsdClearingEntry(token, 130_000L);
        JsonNode exceedsData = ingestUsd(token, "FXT-EXCEEDS", 132_000L); // diff 2000 > 1300
        assertThat(exceedsData.get("matchedCount").asInt()).isEqualTo(1);
        assertThat(exceedsData.get("discrepancyCount").asInt()).isEqualTo(1);
        JsonNode disc = exceedsData.get("discrepancies").get(0);
        assertThat(disc.get("type").asText()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(disc.get("expectedMinor").asText()).isEqualTo("130000");
        assertThat(disc.get("actualMinor").asText()).isEqualTo("132000");
        assertThat(disc.get("currency").asText()).isEqualTo("KRW");

        // (6) Negative bps PUT → 400 VALIDATION_ERROR.
        HttpResponse<String> negative = put(FX_TOLERANCE_PATH, token,
                "{\"toleranceBps\":-1,\"floorMinor\":0}");
        assertThat(negative.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(negative.body()).get("code").asText())
                .isEqualTo("VALIDATION_ERROR");
    }

    /**
     * Wipes the ledger state (as the base {@code @BeforeEach} does) but immediately
     * re-establishes the tolerance config — {@code cleanLedgerState} deletes the
     * tolerance row too, and the within/exceeds legs need it persisted.
     */
    private void cleanLedgerStateButKeepTolerance(String token, int bps, long floor)
            throws Exception {
        cleanLedgerState();
        HttpResponse<String> putResp = put(FX_TOLERANCE_PATH, token,
                "{\"toleranceBps\":" + bps + ",\"floorMinor\":" + floor + "}");
        assertThat(putResp.statusCode()).isEqualTo(200);
    }
}

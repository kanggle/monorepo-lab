package com.example.finance.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Revaluation → lot carrying distribution end-to-end integration (18th increment,
 * TASK-FIN-BE-026 — the authoritative round-trip gate, AC-6/AC-7; ADR-001 D4-a, the
 * task that CLOSES the D4 double-count hazard). Testcontainers MySQL + real Kafka +
 * MockWebServer JWKS. A UNIQUE asset account ({@code FX_REVAL_USD_WALLET}) so its
 * positions never collide with the shared-Kafka sibling ITs' {@code CASH_CLEARING} /
 * {@code FX_FIFO_USD_WALLET} positions.
 *
 * <p>Each acquisition is posted through the guarded write path, so
 * {@code RecordFxAcquisitionLots} materializes one open lot per USD DEBIT line. A
 * revaluation then re-marks every open lot's carrying to spot (the LAST lot absorbing the
 * rounding residual) so {@code Σ open-lot carrying == revaluedBase} (the new aggregate
 * carrying). This makes the subsequent FIN-BE-025 FIFO settlement lot-exact even on a
 * revaluation-touched position (no D4 double-count).
 *
 * <ol>
 *   <li>(AC-1 invariant) a 2-lot USD position (two acquisitions at different rates),
 *       revalue at a closing rate → {@code Σ open-lot carrying == revaluedBase} (= ABS
 *       aggregate carrying from the journal), each lot ≈ {@code round(remaining × rate)}
 *       with the last absorbing the residual;</li>
 *   <li>(AC-2 FIFO × revaluation lot-exact) FIFO config, revalue, then FIFO partial-settle
 *       → {@code C_settle} uses the REVALUED lot carrying (no double-count; realized matches
 *       the hand-computed lot-exact-at-revalued-basis);</li>
 *   <li>(AC-3 net-zero control) WEIGHTED_AVERAGE, the same revalue → settle → realized is
 *       byte-identical to the pre-FIN-BE-026 weighted-average outcome (lots are distributed
 *       but settlement reads the AGGREGATE carrying).</li>
 * </ol>
 */
class LedgerRevaluationLotDistributionIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String FX_ACCOUNT = "FX_REVAL_USD_WALLET";
    private static final String PROCEEDS = "SETTLEMENT_SUSPENSE";

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> post(String path, String token, String idempotencyKey, String body)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        if (idempotencyKey != null) {
            b.header("Idempotency-Key", idempotencyKey);
        }
        b.POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postEntry(String token, String key, String body) throws Exception {
        return post("/api/finance/ledger/entries", token, key, body);
    }

    private HttpResponse<String> postRevaluation(String token, String key, String body) throws Exception {
        return post("/api/finance/ledger/revaluations", token, key, body);
    }

    private HttpResponse<String> postSettlement(String token, String key, String body) throws Exception {
        return post("/api/finance/ledger/settlements", token, key, body);
    }

    /** Seed the unique ASSET account so the manual path (no lazy mint) accepts it. */
    private void seedAssetAccount() {
        jdbcTemplate.update(
                "INSERT INTO ledger_account (code, tenant_id, type, normal_side, created_at) "
                        + "VALUES (?, 'finance', 'ASSET', 'DEBIT', ?) "
                        + "ON DUPLICATE KEY UPDATE code = code",
                FX_ACCOUNT, java.sql.Timestamp.from(Instant.now()));
    }

    private void setCostFlow(String method) {
        jdbcTemplate.update(
                "INSERT INTO fx_cost_flow_config (tenant_id, method, updated_by, updated_at) "
                        + "VALUES ('finance', ?, 'it-operator', ?) "
                        + "ON DUPLICATE KEY UPDATE method = VALUES(method)",
                method, java.sql.Timestamp.from(Instant.now()));
    }

    /** Post a single USD acquisition (DR FX_ACCOUNT foreign@base / CR SETTLEMENT_SUSPENSE base). */
    private void acquire(String token, long foreignMinor, long baseMinor) throws Exception {
        String key = "REVAL-ACQ-" + newId().substring(0, 8);
        String body = "{"
                + "\"reference\":\"REVAL-ACQ\",\"memo\":\"acquire USD\",\"lines\":["
                + "{\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"" + foreignMinor + "\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"" + baseMinor + "\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + PROCEEDS + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"" + baseMinor + "\",\"currency\":\"KRW\"}}"
                + "]}";
        assertThat(postEntry(token, key, body).statusCode()).isEqualTo(201);
    }

    private String revaluationBody(String rate) {
        return "{"
                + "\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"currency\":\"USD\","
                + "\"closingRate\":\"" + rate + "\","
                + "\"reference\":\"REVAL\",\"memo\":\"month-end revaluation\"}";
    }

    private String settlementBody(String rate, String settleForeignAmount) {
        return "{"
                + "\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"currency\":\"USD\","
                + "\"settlementRate\":\"" + rate + "\",\"proceedsAccountCode\":\"" + PROCEEDS + "\","
                + (settleForeignAmount != null
                        ? "\"settleForeignAmount\":\"" + settleForeignAmount + "\"," : "")
                + "\"reference\":\"REVAL-SETTLE\",\"memo\":\"liquidation\"}";
    }

    /** The FX_REVAL_USD_WALLET USD position's foreign balance + base carrying from journal_line. */
    private long[] usdPosition() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT "
                        + "COALESCE(SUM(CASE WHEN direction='DEBIT' THEN amount_minor ELSE 0 END),0) "
                        + "- COALESCE(SUM(CASE WHEN direction='CREDIT' THEN amount_minor ELSE 0 END),0) AS f, "
                        + "COALESCE(SUM(CASE WHEN direction='DEBIT' THEN base_amount_minor ELSE 0 END),0) "
                        + "- COALESCE(SUM(CASE WHEN direction='CREDIT' THEN base_amount_minor ELSE 0 END),0) AS b "
                        + "FROM journal_line WHERE tenant_id='finance' "
                        + "AND ledger_account_code='" + FX_ACCOUNT + "' AND currency='USD'");
        Map<String, Object> r = rows.get(0);
        return new long[]{((Number) r.get("f")).longValue(), ((Number) r.get("b")).longValue()};
    }

    /** The open lots (remaining > 0) of the FX_REVAL_USD_WALLET USD position, FIFO-ordered. */
    private List<Map<String, Object>> openLots() {
        return jdbcTemplate.queryForList(
                "SELECT remaining_foreign_minor, carrying_base_minor, seq "
                        + "FROM fx_position_lot WHERE tenant_id='finance' "
                        + "AND ledger_account_code='" + FX_ACCOUNT + "' AND currency='USD' "
                        + "AND remaining_foreign_minor > 0 ORDER BY acquired_at ASC, seq ASC");
    }

    private long sumOpenLotCarrying() {
        return openLots().stream().mapToLong(l -> asLong(l.get("carrying_base_minor"))).sum();
    }

    /**
     * Delete all lot rows for the revaluation account. A WEIGHTED_AVERAGE settlement never
     * consumes lots (it reads the aggregate carrying — the net-zero property), so after a
     * weighted-average full-settle the journal position is (0,0) but the now-stale lots
     * still read {@code remaining > 0}. Clearing them between scenarios reproduces a
     * genuinely lot-free start (mirrors {@code LedgerFifoSettlementIntegrationTest#clearLots}).
     */
    private void clearLots() {
        jdbcTemplate.update(
                "DELETE FROM fx_position_lot WHERE tenant_id='finance' "
                        + "AND ledger_account_code=?", FX_ACCOUNT);
    }

    private static long asLong(Object v) {
        return ((Number) v).longValue();
    }

    @Test
    void revaluationDistributesCarryingToLotsKeepingTheSumInvariant() throws Exception {
        String token = financeReadToken();
        seedAssetAccount();

        // ============ (AC-1) invariant: Σ open-lot carrying == revaluedBase ============
        // Build a 2-lot USD position: 1000 USD @ 1300 (1,300,000) then 1000 USD @ 1400 (1,400,000).
        acquire(token, 1_000L, 1_300_000L);   // lot1 — oldest
        acquire(token, 1_000L, 1_400_000L);   // lot2
        long[] before = usdPosition();
        assertThat(before[0]).isEqualTo(2_000L);
        assertThat(before[1]).isEqualTo(2_700_000L);  // pool carrying (avg 1350/USD)
        assertThat(openLots()).hasSize(2);
        // Pre-revaluation: Σ lot carrying == pool carrying (the acquisition-time invariant).
        assertThat(sumOpenLotCarrying()).isEqualTo(2_700_000L);

        // Revalue @ closing 1500 → revaluedBase = round(2000 × 1500) = 3,000,000; delta +300,000.
        HttpResponse<String> rReval = postRevaluation(token, "REVAL-1-" + newId().substring(0, 8),
                revaluationBody("1500"));
        assertThat(rReval.statusCode()).isEqualTo(201);
        JsonNode bReval = objectMapper.readTree(rReval.body());
        assertThat(bReval.at("/data/revalued").asBoolean()).isTrue();
        assertThat(bReval.at("/data/deltaBaseMinor").asText()).isEqualTo("300000");
        assertThat(bReval.at("/data/outcome").asText()).isEqualTo("FX_GAIN");

        // The aggregate carrying is now revaluedBase = 3,000,000 (foreign unchanged @ 2000).
        long[] afterReval = usdPosition();
        assertThat(afterReval[0]).isEqualTo(2_000L);
        assertThat(afterReval[1]).isEqualTo(3_000_000L);  // 2,700,000 + 300,000

        // The lots are re-marked to spot: lot1 1000 × 1500 = 1,500,000; lot2(last) absorbs
        // 3,000,000 − 1,500,000 = 1,500,000. Σ == revaluedBase == |aggregate carrying|.
        List<Map<String, Object>> lots = openLots();
        assertThat(lots).hasSize(2);
        assertThat(asLong(lots.get(0).get("carrying_base_minor"))).isEqualTo(1_500_000L);
        assertThat(asLong(lots.get(1).get("carrying_base_minor"))).isEqualTo(1_500_000L);
        assertThat(sumOpenLotCarrying()).isEqualTo(3_000_000L);   // == revaluedBase (AC-1)
        // remaining foreign untouched by the revaluation.
        assertThat(asLong(lots.get(0).get("remaining_foreign_minor"))).isEqualTo(1_000L);
        assertThat(asLong(lots.get(1).get("remaining_foreign_minor"))).isEqualTo(1_000L);

        // Net the position to zero for a clean AC-2 baseline. The default (weighted-average)
        // settle does NOT consume lots (it reads the aggregate), so clear the stale lots it
        // leaves behind — otherwise they would pollute the AC-2 open-lot set.
        assertThat(postSettlement(token, "RESET-1-" + newId().substring(0, 8),
                settlementBody("1500", null)).statusCode()).isEqualTo(201);
        assertThat(usdPosition()[0]).isZero();
        clearLots();
        assertThat(openLots()).isEmpty();

        // ============ (AC-2) FIFO × revaluation lot-exact (no double-count) ============
        setCostFlow("FIFO");
        acquire(token, 1_000L, 1_300_000L);   // lot1 — oldest @ 1300
        acquire(token, 1_000L, 1_400_000L);   // lot2 @ 1400
        assertThat(usdPosition()[0]).isEqualTo(2_000L);
        assertThat(usdPosition()[1]).isEqualTo(2_700_000L);

        // Revalue @ 1500 → revaluedBase 3,000,000; lots re-marked to 1,500,000 each.
        assertThat(postRevaluation(token, "REVAL-2-" + newId().substring(0, 8),
                revaluationBody("1500")).statusCode()).isEqualTo(201);
        assertThat(usdPosition()[1]).isEqualTo(3_000_000L);
        assertThat(sumOpenLotCarrying()).isEqualTo(3_000_000L);

        // FIFO partial-settle 1500 USD @ spot 1600 → proceeds 2,400,000.
        // C_settle (FIFO, REVALUED basis) = lot1 1,500,000 (full) + round(1,500,000 × 500/1000)
        //   = 1,500,000 + 750,000 = 2,250,000  (NOT the pre-revaluation 2,000,000 — proves the
        //   distribution made the lots reflect spot; NOT the pool average either).
        // realized = 2,400,000 − 2,250,000 = 150,000.
        HttpResponse<String> rSettle = postSettlement(token, "FIFO-1-" + newId().substring(0, 8),
                settlementBody("1600", "1500"));
        assertThat(rSettle.statusCode()).isEqualTo(201);
        JsonNode bSettle = objectMapper.readTree(rSettle.body());
        assertThat(bSettle.at("/data/proceedsBaseMinor").asText()).isEqualTo("2400000");
        assertThat(bSettle.at("/data/realizedBaseMinor").asText()).isEqualTo("150000"); // lot-exact @ revalued
        assertThat(bSettle.at("/data/outcome").asText()).isEqualTo("FX_GAIN");
        // residual lot-exact: (2000 − 1500, 3,000,000 − 2,250,000) = (500, 750,000) — lot2's remainder.
        assertThat(bSettle.at("/data/residualForeignMinor").asText()).isEqualTo("500");
        assertThat(bSettle.at("/data/residualCarryingBaseMinor").asText()).isEqualTo("750000");

        // Oldest lot consumed first; lot2 has 500 / 750,000 (revalued) remaining.
        List<Map<String, Object>> postSettleLots = openLots();
        assertThat(postSettleLots).hasSize(1);
        assertThat(asLong(postSettleLots.get(0).get("remaining_foreign_minor"))).isEqualTo(500L);
        assertThat(asLong(postSettleLots.get(0).get("carrying_base_minor"))).isEqualTo(750_000L);

        // Net to zero for the AC-3 baseline. FIFO consumed the lots here, but clear any
        // residue defensively so AC-3 starts genuinely lot-free.
        assertThat(postSettlement(token, "RESET-2-" + newId().substring(0, 8),
                settlementBody("1600", null)).statusCode()).isEqualTo(201);
        assertThat(usdPosition()[0]).isZero();
        clearLots();
        assertThat(openLots()).isEmpty();

        // ============ (AC-3) net-zero control: WEIGHTED_AVERAGE settlement reads aggregate ============
        // The lots are STILL distributed by the revaluation, but weighted-average settlement
        // derives C_settle from the AGGREGATE carrying — so the realized is byte-identical to the
        // pre-FIN-BE-026 weighted-average outcome (lot distribution is invisible to it).
        setCostFlow("WEIGHTED_AVERAGE");
        acquire(token, 1_000L, 1_300_000L);
        acquire(token, 1_000L, 1_400_000L);
        assertThat(usdPosition()[1]).isEqualTo(2_700_000L);

        // Revalue @ 1500 → aggregate carrying 3,000,000; lots re-marked (1,500,000 each).
        assertThat(postRevaluation(token, "REVAL-3-" + newId().substring(0, 8),
                revaluationBody("1500")).statusCode()).isEqualTo(201);
        assertThat(usdPosition()[1]).isEqualTo(3_000_000L);
        assertThat(sumOpenLotCarrying()).isEqualTo(3_000_000L);   // distributed (AC-1 holds here too)

        // Weighted-average settle 1500 USD @ 1600 → proceeds 2,400,000.
        // C_settle = round(3,000,000 × 1500/2000) = 2,250,000 (the AGGREGATE basis — identical to
        // what the lot-exact FIFO computed here only because the rate is uniform; the point is it
        // reads the aggregate, NOT the lots). realized = 2,400,000 − 2,250,000 = 150,000.
        HttpResponse<String> rAvg = postSettlement(token, "AVG-1-" + newId().substring(0, 8),
                settlementBody("1600", "1500"));
        assertThat(rAvg.statusCode()).isEqualTo(201);
        JsonNode bAvg = objectMapper.readTree(rAvg.body());
        assertThat(bAvg.at("/data/proceedsBaseMinor").asText()).isEqualTo("2400000");
        assertThat(bAvg.at("/data/realizedBaseMinor").asText()).isEqualTo("150000");
        // Weighted-average residual carrying = aggregate 3,000,000 − 2,250,000 = 750,000 (reads
        // the aggregate; the lot distribution did NOT change this).
        assertThat(bAvg.at("/data/residualCarryingBaseMinor").asText()).isEqualTo("750000");
    }
}

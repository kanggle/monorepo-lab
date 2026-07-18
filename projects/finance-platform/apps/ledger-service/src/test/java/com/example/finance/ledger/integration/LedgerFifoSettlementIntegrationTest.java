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
 * FIFO lot-consumption settlement end-to-end integration (17th increment,
 * TASK-FIN-BE-025 — the authoritative round-trip gate, AC-7/AC-8; ADR-001 D3).
 * Testcontainers MySQL + real Kafka + MockWebServer JWKS. A UNIQUE asset account
 * ({@code FX_FIFO_USD_WALLET}) so its positions never collide with the shared-Kafka
 * sibling ITs' {@code CASH_CLEARING} positions.
 *
 * <p>The proceeds account is the already-seeded {@code SETTLEMENT_SUSPENSE}. Each
 * acquisition is posted through the guarded write path, so {@code RecordFxAcquisitionLots}
 * materializes one open lot per USD DEBIT line; the FIFO walk then consumes them
 * oldest-first. <b>No revaluation interleaving</b> — so {@code Σ open-lot carrying ==
 * position carrying C} (the D4 invariant FIN-BE-026 maintains under revaluation), making
 * the lot-exact assertions valid.
 *
 * <ol>
 *   <li>(AC-1) FIFO config + a 2-lot position (1000 USD @ 1,300,000 then 1000 USD @
 *       1,400,000), partial-settle 1500 USD @ spot 1500 → oldest lot consumed first,
 *       {@code C_settle = 2,000,000} (the FIFO slice, NOT the pool average 2,025,000),
 *       realized 250,000, consumed lot remaining/carrying decremented;</li>
 *   <li>(AC-2) full settle of the residual → all lots consumed, residual (0,0), realized =
 *       proceeds − Σ lot carrying;</li>
 *   <li>(AC-3 control) the SAME 2-lot scenario under WEIGHTED_AVERAGE → {@code C_settle}
 *       2,025,000 / realized 225,000 — DIFFERS from FIFO (proves the branch matters) and
 *       equals the pre-existing weighted-average formula;</li>
 *   <li>(AC-4) FIFO configured but the position has no lots → falls back to weighted-average
 *       and settles successfully (no net-non-zero).</li>
 * </ol>
 */
class LedgerFifoSettlementIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String FX_ACCOUNT = "FX_FIFO_USD_WALLET";
    private static final String PROCEEDS = "SETTLEMENT_SUSPENSE";

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> postEntry(String token, String idempotencyKey, String body)
            throws Exception {
        return post("/api/finance/ledger/entries", token, idempotencyKey, body);
    }

    private HttpResponse<String> postSettlement(String token, String idempotencyKey, String body)
            throws Exception {
        return post("/api/finance/ledger/settlements", token, idempotencyKey, body);
    }

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

    /** Seed the unique ASSET account so the manual path (no lazy mint) accepts it. */
    private void seedAssetAccount() {
        seedAssetAccount(FX_ACCOUNT);
    }

    /** Post a single USD acquisition (DR FX_ACCOUNT foreign@base / CR SETTLEMENT_SUSPENSE base). */
    private void acquire(String token, long foreignMinor, long baseMinor) throws Exception {
        String key = "FIFO-ACQ-" + newId().substring(0, 8);
        String body = "{"
                + "\"reference\":\"FIFO-ACQ\",\"memo\":\"acquire USD\",\"lines\":["
                + "{\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"" + foreignMinor + "\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"" + baseMinor + "\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + PROCEEDS + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"" + baseMinor + "\",\"currency\":\"KRW\"}}"
                + "]}";
        assertThat(postEntry(token, key, body).statusCode()).isEqualTo(201);
    }

    private String settlementBody(String rate, String settleForeignAmount) {
        return "{"
                + "\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"currency\":\"USD\","
                + "\"settlementRate\":\"" + rate + "\",\"proceedsAccountCode\":\"" + PROCEEDS + "\","
                + (settleForeignAmount != null
                        ? "\"settleForeignAmount\":\"" + settleForeignAmount + "\"," : "")
                + "\"reference\":\"FIFO-SETTLE\",\"memo\":\"FIFO liquidation\"}";
    }

    /** The FX_FIFO_USD_WALLET USD position's foreign balance + base carrying from journal_line. */
    private long[] usdPosition() {
        return positionFor(FX_ACCOUNT, "USD");
    }

    /** The open lots (remaining > 0) of the FX_FIFO_USD_WALLET USD position, FIFO-ordered. */
    private List<Map<String, Object>> openLots() {
        return openLots(FX_ACCOUNT);
    }

    /** Delete all lot rows for the FIFO account (clears weighted-average shadow-desync residue). */
    private void clearLots() {
        jdbcTemplate.update(
                "DELETE FROM fx_position_lot WHERE tenant_id='finance' "
                        + "AND ledger_account_code=?", FX_ACCOUNT);
    }

    private static long asLong(Object v) {
        return ((Number) v).longValue();
    }

    @Test
    void fifoSettlementConsumesOldestLotsFirstAndFallsBackOnShortfall() throws Exception {
        String token = financeWriteToken();
        seedAssetAccount();

        // ============ (AC-1) FIFO lot-exact partial settle ============
        setCostFlow("FIFO");
        acquire(token, 1_000L, 1_300_000L);   // lot1 — oldest @ 1300/USD
        acquire(token, 1_000L, 1_400_000L);   // lot2 — @ 1400/USD
        long[] before = usdPosition();
        assertThat(before[0]).isEqualTo(2_000L);
        assertThat(before[1]).isEqualTo(2_700_000L);  // pool carrying (avg 1350/USD)
        assertThat(openLots()).hasSize(2);

        // Settle 1500 USD @ spot 1500 → proceeds 2,250,000.
        // FIFO C_settle = lot1 1,300,000 (full) + round(1,400,000×500/1000)=700,000 = 2,000,000.
        // realized = 2,250,000 − 2,000,000 = 250,000 (FIFO; the pool avg would give 225,000).
        HttpResponse<String> r1 = postSettlement(token, "FIFO-1-" + newId().substring(0, 8),
                settlementBody("1500", "1500"));
        assertThat(r1.statusCode()).isEqualTo(201);
        JsonNode b1 = objectMapper.readTree(r1.body());
        assertThat(b1.at("/data/settled").asBoolean()).isTrue();
        assertThat(b1.at("/data/proceedsBaseMinor").asText()).isEqualTo("2250000");
        assertThat(b1.at("/data/realizedBaseMinor").asText()).isEqualTo("250000");   // FIFO, not 225000
        assertThat(b1.at("/data/outcome").asText()).isEqualTo("FX_GAIN");
        // residual is lot-exact: (2000 − 1500, 2,700,000 − 2,000,000) = (500, 700,000) = lot2's remainder.
        assertThat(b1.at("/data/residualForeignMinor").asText()).isEqualTo("500");
        assertThat(b1.at("/data/residualCarryingBaseMinor").asText()).isEqualTo("700000");

        // The journal position residual matches (lot-exact, AC-5).
        long[] afterPartial = usdPosition();
        assertThat(afterPartial[0]).isEqualTo(500L);
        assertThat(afterPartial[1]).isEqualTo(700_000L);

        // Oldest lot consumed first: lot1 fully gone, lot2 has 500 / 700,000 remaining.
        List<Map<String, Object>> lots = openLots();
        assertThat(lots).hasSize(1);
        assertThat(asLong(lots.get(0).get("remaining_foreign_minor"))).isEqualTo(500L);
        assertThat(asLong(lots.get(0).get("carrying_base_minor"))).isEqualTo(700_000L);

        // ============ (AC-2) full settle of the residual ============
        // Settle the remaining 500 USD @ spot 1600 (omit amount = full) → proceeds 800,000,
        // realized = 800,000 − 700,000 (Σ remaining lot carrying) = 100,000. Position to (0,0).
        HttpResponse<String> r2 = postSettlement(token, "FIFO-2-" + newId().substring(0, 8),
                settlementBody("1600", null));
        assertThat(r2.statusCode()).isEqualTo(201);
        JsonNode b2 = objectMapper.readTree(r2.body());
        assertThat(b2.at("/data/proceedsBaseMinor").asText()).isEqualTo("800000");
        assertThat(b2.at("/data/realizedBaseMinor").asText()).isEqualTo("100000");
        assertThat(b2.at("/data/residualForeignMinor").asText()).isEqualTo("0");
        assertThat(b2.at("/data/residualCarryingBaseMinor").asText()).isEqualTo("0");
        long[] afterFull = usdPosition();
        assertThat(afterFull[0]).isZero();
        assertThat(afterFull[1]).isZero();
        assertThat(openLots()).isEmpty();   // all lots consumed

        // ============ (AC-3 control) the SAME scenario under WEIGHTED_AVERAGE ============
        setCostFlow("WEIGHTED_AVERAGE");
        acquire(token, 1_000L, 1_300_000L);   // rebuild the identical 2-lot position
        acquire(token, 1_000L, 1_400_000L);
        assertThat(usdPosition()[0]).isEqualTo(2_000L);
        assertThat(usdPosition()[1]).isEqualTo(2_700_000L);

        // Settle 1500 USD @ 1500. Weighted-average C_settle = round(2,700,000 × 1500/2000) = 2,025,000;
        // realized = 2,250,000 − 2,025,000 = 225,000 — DIFFERS from the FIFO 250,000 (the branch matters).
        HttpResponse<String> rAvg = postSettlement(token, "AVG-" + newId().substring(0, 8),
                settlementBody("1500", "1500"));
        assertThat(rAvg.statusCode()).isEqualTo(201);
        JsonNode bAvg = objectMapper.readTree(rAvg.body());
        assertThat(bAvg.at("/data/proceedsBaseMinor").asText()).isEqualTo("2250000");
        assertThat(bAvg.at("/data/realizedBaseMinor").asText()).isEqualTo("225000");  // != 250000 (FIFO)
        assertThat(bAvg.at("/data/residualCarryingBaseMinor").asText()).isEqualTo("675000"); // 2,700,000 − 2,025,000
        // Net the residual back to zero for a clean baseline.
        assertThat(postSettlement(token, "AVG-FULL-" + newId().substring(0, 8),
                settlementBody("1500", null)).statusCode()).isEqualTo(201);
        assertThat(usdPosition()[0]).isZero();
        // The weighted-average path never consumed lots (shadow desync) — clear the stale lot
        // rows it left behind so AC-4 can reproduce a genuinely lot-free position.
        clearLots();

        // ============ (AC-4) FIFO configured but no lots for the position → fallback ============
        // Seed a hook-free position by DIRECT journal_line insert (bypasses RecordFxAcquisitionLots),
        // so the position exists with NO lot row → Σremaining 0 < |F_settle| → weighted-average fallback.
        setCostFlow("FIFO");
        seedHookFreePosition(1_000L, 1_300_000L);   // +1000 USD @ 1,300,000, no lot created
        assertThat(usdPosition()[0]).isEqualTo(1_000L);
        assertThat(usdPosition()[1]).isEqualTo(1_300_000L);
        assertThat(openLots()).isEmpty();           // confirm no lot backing the position

        // Full settle @ 1400 → fallback weighted-average C_settle == C == 1,300,000; proceeds 1,400,000,
        // realized 100,000. Settles successfully (no net-non-zero) despite the FIFO config.
        HttpResponse<String> rFallback = postSettlement(token, "FB-" + newId().substring(0, 8),
                settlementBody("1400", null));
        assertThat(rFallback.statusCode()).isEqualTo(201);
        JsonNode bFb = objectMapper.readTree(rFallback.body());
        assertThat(bFb.at("/data/settled").asBoolean()).isTrue();
        assertThat(bFb.at("/data/proceedsBaseMinor").asText()).isEqualTo("1400000");
        assertThat(bFb.at("/data/realizedBaseMinor").asText()).isEqualTo("100000");
        assertThat(usdPosition()[0]).isZero();      // position removed
        assertThat(usdPosition()[1]).isZero();
    }

    /**
     * Seed a hook-bypassing USD DEBIT position by direct journal_line insert (a parent
     * journal_entry first for the FK). This deliberately routes AROUND the guarded write
     * path so NO {@code fx_position_lot} row is created — reproducing the lot-shortfall the
     * FIFO fallback must absorb (AC-4).
     */
    private void seedHookFreePosition(long foreignMinor, long baseMinor) {
        String entryId = newId();
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO journal_entry (entry_id, tenant_id, posted_at, source_type, "
                        + "source_transaction_id, source_event_id, version) "
                        + "VALUES (?, 'finance', ?, 'MANUAL', ?, ?, 0)",
                entryId, now, "seed-txn-" + newId().substring(0, 8), "seed-evt-" + newId().substring(0, 8));
        jdbcTemplate.update(
                "INSERT INTO journal_line (entry_id, tenant_id, ledger_account_code, direction, "
                        + "amount_minor, currency, posted_at, exchange_rate, base_amount_minor, base_currency) "
                        + "VALUES (?, 'finance', ?, 'DEBIT', ?, 'USD', ?, ?, ?, 'KRW')",
                entryId, FX_ACCOUNT, foreignMinor, now,
                new java.math.BigDecimal(baseMinor).divide(new java.math.BigDecimal(foreignMinor),
                        8, java.math.RoundingMode.HALF_UP),
                baseMinor);
    }
}

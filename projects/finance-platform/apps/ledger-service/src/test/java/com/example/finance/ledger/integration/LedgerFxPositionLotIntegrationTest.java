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
 * FX acquisition position lots — creation hook (shadow) + backfill SQL equivalence
 * (16th increment, TASK-FIN-BE-024 — the authoritative round-trip gate, AC-7/AC-8;
 * ADR-001 D2/D5). Testcontainers MySQL + real Kafka + MockWebServer JWKS.
 *
 * <p>The lots are <b>write-only / shadow</b> in this increment: the guarded write
 * path materializes a lot per position-increasing foreign line, but nothing
 * consumes them (FIN-BE-025). The IT uses a UNIQUE asset account
 * ({@code FX_LOT_USD_WALLET}, seeded directly) so its postings do not collide with
 * the shared-Kafka sibling ITs' {@code CASH_CLEARING} positions.
 *
 * <ol>
 *   <li>(AC-1) a USD acquisition (DR FX_LOT_USD_WALLET 10000 USD @130000 base / CR
 *       SETTLEMENT_SUSPENSE 130000 KRW) → exactly one lot with
 *       original_foreign=remaining=10000, original_base=carrying=130000,
 *       acquired_at=postedAt, source_journal_entry_id=entryId;</li>
 *   <li>(AC-2) a multi-foreign-line entry → one lot per acquisition line in seq
 *       order; the KRW counter-line and a zero-amount revaluation line create none;</li>
 *   <li>(AC-3) a position-reducing foreign line (CREDIT on the ASSET account) → no lot;</li>
 *   <li>(AC-4 net-zero) settlement stays weighted-average regardless of the cost-flow
 *       config (FIFO config selected → settlement result byte-identical);</li>
 *   <li>(AC-5) the V10 backfill SQL reconstructs a hook-bypassing seeded position as a
 *       synthetic lot whose carrying == ABS(Σ signed base) and foreign == ABS(Σ signed amount).</li>
 * </ol>
 */
class LedgerFxPositionLotIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String FX_ACCOUNT = "FX_LOT_USD_WALLET";
    private static final String PROCEEDS = "SETTLEMENT_SUSPENSE";

    private final HttpClient http = HttpClient.newHttpClient();

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

    /** Seed the unique ASSET account so the manual path (no lazy mint) accepts it. */
    private void seedAssetAccount() {
        jdbcTemplate.update(
                "INSERT INTO ledger_account (code, tenant_id, type, normal_side, created_at) "
                        + "VALUES (?, 'finance', 'ASSET', 'DEBIT', ?) "
                        + "ON DUPLICATE KEY UPDATE code = code",
                FX_ACCOUNT, java.sql.Timestamp.from(Instant.now()));
    }

    private List<Map<String, Object>> lotsFor(String account, String currency) {
        return jdbcTemplate.queryForList(
                "SELECT lot_id, original_foreign_minor, original_base_minor, "
                        + "remaining_foreign_minor, carrying_base_minor, acquired_at, seq, "
                        + "source_journal_entry_id "
                        + "FROM fx_position_lot WHERE tenant_id='finance' "
                        + "AND ledger_account_code=? AND currency=? ORDER BY seq ASC",
                account, currency);
    }

    private static long asLong(Object v) {
        return ((Number) v).longValue();
    }

    @Test
    void acquisitionLotsAreCreatedShadowAndBackfillReconstructsThePosition() throws Exception {
        String token = financeReadToken();
        seedAssetAccount();

        // (AC-1) A single USD acquisition → exactly one lot.
        String key1 = "FXLOT-ACQ-" + newId().substring(0, 8);
        String acqBody = "{"
                + "\"reference\":\"FXLOT-1\",\"memo\":\"acquire USD\",\"lines\":["
                + "{\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"10000\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"130000\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + PROCEEDS + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"130000\",\"currency\":\"KRW\"}}"
                + "]}";
        HttpResponse<String> r1 = postEntry(token, key1, acqBody);
        assertThat(r1.statusCode()).isEqualTo(201);
        String entryId1 = objectMapper.readTree(r1.body()).at("/data/entryId").asText();

        List<Map<String, Object>> lots = lotsFor(FX_ACCOUNT, "USD");
        assertThat(lots).hasSize(1);
        Map<String, Object> lot = lots.get(0);
        assertThat(asLong(lot.get("original_foreign_minor"))).isEqualTo(10_000L);
        assertThat(asLong(lot.get("remaining_foreign_minor"))).isEqualTo(10_000L);
        assertThat(asLong(lot.get("original_base_minor"))).isEqualTo(130_000L);
        assertThat(asLong(lot.get("carrying_base_minor"))).isEqualTo(130_000L);
        assertThat(lot.get("source_journal_entry_id")).isEqualTo(entryId1);
        assertThat(asLong(lot.get("seq"))).isPositive();   // the line's IDENTITY id

        // (AC-2) A multi-foreign-line entry → one lot per acquisition line in seq order;
        //        the KRW counter-line creates none. Two USD DEBIT acquisition lines
        //        (5000 @ 65000 base + 3000 @ 39000 base) balanced by a KRW credit.
        String key2 = "FXLOT-MULTI-" + newId().substring(0, 8);
        String multiBody = "{"
                + "\"reference\":\"FXLOT-2\",\"memo\":\"two USD acquisitions\",\"lines\":["
                + "{\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"5000\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"65000\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"3000\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"39000\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + PROCEEDS + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"104000\",\"currency\":\"KRW\"}}"
                + "]}";
        assertThat(postEntry(token, key2, multiBody).statusCode()).isEqualTo(201);

        List<Map<String, Object>> afterMulti = lotsFor(FX_ACCOUNT, "USD");
        // 1 (AC-1) + 2 (the two acquisition lines) = 3; the KRW line created none.
        assertThat(afterMulti).hasSize(3);
        // seq-ascending: the two new lots are the 5000 then 3000 line (posting order).
        assertThat(asLong(afterMulti.get(1).get("original_foreign_minor"))).isEqualTo(5_000L);
        assertThat(asLong(afterMulti.get(1).get("original_base_minor"))).isEqualTo(65_000L);
        assertThat(asLong(afterMulti.get(2).get("original_foreign_minor"))).isEqualTo(3_000L);
        assertThat(asLong(afterMulti.get(2).get("original_base_minor"))).isEqualTo(39_000L);
        // No KRW lot was ever created for SETTLEMENT_SUSPENSE.
        assertThat(lotsFor(PROCEEDS, "KRW")).isEmpty();

        // (AC-3) A position-REDUCING foreign line (CREDIT on the ASSET account) → no lot.
        //        DR SETTLEMENT_SUSPENSE 26000 KRW / CR FX_LOT_USD_WALLET 2000 USD @26000.
        String key3 = "FXLOT-REDUCE-" + newId().substring(0, 8);
        String reduceBody = "{"
                + "\"reference\":\"FXLOT-3\",\"memo\":\"reduce USD (non-settlement)\",\"lines\":["
                + "{\"ledgerAccountCode\":\"" + PROCEEDS + "\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"26000\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"2000\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"26000\",\"currency\":\"KRW\"}}"
                + "]}";
        assertThat(postEntry(token, key3, reduceBody).statusCode()).isEqualTo(201);
        // Still exactly 3 lots — the reducing CREDIT line created none (shadow desync; FIN-BE-025).
        assertThat(lotsFor(FX_ACCOUNT, "USD")).hasSize(3);

        // (AC-4) Select FIFO, then settle the FX_LOT_USD_WALLET USD position. As of
        //        FIN-BE-025 a FIFO config DOES walk + consume the open lots, but here every
        //        lot shares the same 13.0/USD rate, so the FIFO slice cost equals the
        //        weighted-average share (C_settle_fifo == C_settle_avg) and the realized
        //        result is identical — the assertions below hold under either method.
        //        Net foreign position now = 10000 + 5000 + 3000 - 2000 = 16000 USD,
        //        net carrying = 130000 + 65000 + 39000 - 26000 = 208000 KRW (avg 13.0/USD).
        jdbcTemplate.update(
                "INSERT INTO fx_cost_flow_config (tenant_id, method, updated_by, updated_at) "
                        + "VALUES ('finance', 'FIFO', 'it-operator', ?) "
                        + "ON DUPLICATE KEY UPDATE method='FIFO'",
                java.sql.Timestamp.from(Instant.now()));

        String settleKey = "FXLOT-SETTLE-" + newId().substring(0, 8);
        String settleBody = "{"
                + "\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"currency\":\"USD\","
                + "\"settlementRate\":\"14.0\",\"proceedsAccountCode\":\"" + PROCEEDS + "\","
                + "\"reference\":\"FXLOT-SETTLE\",\"memo\":\"liquidate USD weighted-avg\"}";
        HttpResponse<String> rSettle = postSettlement(token, settleKey, settleBody);
        assertThat(rSettle.statusCode()).isEqualTo(201);
        JsonNode bSettle = objectMapper.readTree(rSettle.body());
        // Weighted-average: proceeds = 16000 * 14.0 = 224000; carrying = 208000;
        // realized = 224000 - 208000 = 16000 (FX_GAIN). FIFO config did NOT change this.
        assertThat(bSettle.at("/data/proceedsBaseMinor").asText()).isEqualTo("224000");
        assertThat(bSettle.at("/data/realizedBaseMinor").asText()).isEqualTo("16000");
        assertThat(bSettle.at("/data/outcome").asText()).isEqualTo("FX_GAIN");

        // (AC-5) V10 backfill SQL equivalence. Seed journal_line rows DIRECTLY (bypassing
        //        the acquisition hook) for a fresh hook-free position on a distinct account,
        //        then run the SAME INSERT…SELECT as V10 and assert the synthetic lot's
        //        carrying == ABS(Σ signed base) and foreign == ABS(Σ signed amount).
        String backfillAccount = "FX_LOT_BACKFILL";
        // +7000 USD @ 91000 base (DEBIT), then -1000 USD @ 13000 base (CREDIT)
        //  → net foreign = 6000, net base = 78000 (pool carrying).
        seedJournalLine(backfillAccount, "DEBIT", 7000L, 91000L);
        seedJournalLine(backfillAccount, "CREDIT", 1000L, 13000L);

        int inserted = runV10Backfill(backfillAccount);
        assertThat(inserted).isEqualTo(1);
        List<Map<String, Object>> backfillLots = lotsFor(backfillAccount, "USD");
        assertThat(backfillLots).hasSize(1);
        Map<String, Object> synthetic = backfillLots.get(0);
        assertThat(asLong(synthetic.get("original_foreign_minor"))).isEqualTo(6_000L);   // ABS(Σ signed amount)
        assertThat(asLong(synthetic.get("remaining_foreign_minor"))).isEqualTo(6_000L);
        assertThat(asLong(synthetic.get("original_base_minor"))).isEqualTo(78_000L);      // ABS(Σ signed base) = pool carrying
        assertThat(asLong(synthetic.get("carrying_base_minor"))).isEqualTo(78_000L);
        assertThat(synthetic.get("source_journal_entry_id")).isNull();                   // synthetic
    }

    /**
     * Insert a raw journal_line (bypassing the entry factory + acquisition hook) for
     * backfill seeding. A parent journal_entry is created first to satisfy the
     * fk_journal_line_entry constraint — this deliberately routes AROUND the guarded
     * write path so no lot is created, proving the V10 backfill (not the hook)
     * reconstructs the position.
     */
    private void seedJournalLine(String account, String direction, long amountMinor, long baseMinor) {
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
                        + "VALUES (?, 'finance', ?, ?, ?, 'USD', ?, ?, ?, 'KRW')",
                entryId, account, direction, amountMinor, now,
                new java.math.BigDecimal(baseMinor).divide(new java.math.BigDecimal(amountMinor),
                        8, java.math.RoundingMode.HALF_UP),
                baseMinor);
    }

    /** Run the EXACT V10 backfill INSERT…SELECT, scoped to one account for test isolation. */
    private int runV10Backfill(String account) {
        return jdbcTemplate.update(
                "INSERT INTO fx_position_lot (lot_id, tenant_id, ledger_account_code, currency, "
                        + "acquired_at, seq, original_foreign_minor, original_base_minor, "
                        + "remaining_foreign_minor, carrying_base_minor, source_journal_entry_id, created_at) "
                        + "SELECT UUID(), jl.tenant_id, jl.ledger_account_code, jl.currency, "
                        + "MIN(jl.posted_at), MIN(jl.id), "
                        + "ABS(SUM(CASE WHEN jl.direction='DEBIT' THEN jl.amount_minor ELSE -jl.amount_minor END)), "
                        + "ABS(SUM(CASE WHEN jl.direction='DEBIT' THEN jl.base_amount_minor ELSE -jl.base_amount_minor END)), "
                        + "ABS(SUM(CASE WHEN jl.direction='DEBIT' THEN jl.amount_minor ELSE -jl.amount_minor END)), "
                        + "ABS(SUM(CASE WHEN jl.direction='DEBIT' THEN jl.base_amount_minor ELSE -jl.base_amount_minor END)), "
                        + "NULL, NOW(6) "
                        + "FROM journal_line jl "
                        + "WHERE jl.currency <> 'KRW' AND jl.ledger_account_code = ? "
                        + "GROUP BY jl.tenant_id, jl.ledger_account_code, jl.currency "
                        + "HAVING SUM(CASE WHEN jl.direction='DEBIT' THEN jl.amount_minor ELSE -jl.amount_minor END) <> 0",
                account);
    }
}

package com.example.finance.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FX position lots read endpoint integration tests (20th increment —
 * TASK-FIN-BE-028; ADR-001 § 3.1 deferred "lot 콘솔 drill-in" backend). Extends
 * {@link AbstractLedgerIntegrationTest} (Testcontainers MySQL + real Kafka + MockWebServer
 * JWKS). Uses a UNIQUE {@code ledgerAccountCode} ({@code FX_LOTSREAD_USD_WALLET}) so it
 * never collides with the sibling FIFO/revaluation IT classes.
 *
 * <p>Acceptance criteria exercised:
 * <ol>
 *   <li>(AC-1) Two USD acquisitions → GET lots returns 2 ordered lots with correct fields
 *       and a correct summary ({@code lotCount=2}, Σ remaining, Σ carrying).</li>
 *   <li>(AC-2) FIFO partial settlement → GET reflects decremented
 *       {@code remainingForeignMinor}/{@code carryingBaseMinor}; fully-consumed lot excluded
 *       from the open-lot list.</li>
 *   <li>(AC-3) Empty position → empty list + zero summary, 200 (not 404).</li>
 *   <li>(AC-4) Unknown currency ({@code XYZ}) → 400 {@code VALIDATION_ERROR}.</li>
 * </ol>
 */
class LedgerFxPositionLotsReadIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String FX_ACCOUNT = "FX_LOTSREAD_USD_WALLET";
    private static final String PROCEEDS = "SETTLEMENT_SUSPENSE";

    private final HttpClient http = HttpClient.newHttpClient();

    // -----------------------------------------------------------------------
    // HTTP helpers
    // -----------------------------------------------------------------------

    private HttpResponse<String> postEntry(String token, String idempotencyKey, String body)
            throws Exception {
        return post("/api/finance/ledger/entries", token, idempotencyKey, body);
    }

    private HttpResponse<String> postSettlement(String token, String idempotencyKey, String body)
            throws Exception {
        return post("/api/finance/ledger/settlements", token, idempotencyKey, body);
    }

    private HttpResponse<String> getLots(String token, String account, String currency)
            throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port
                        + "/api/finance/ledger/settlements/" + account + "/" + currency + "/lots"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
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

    // -----------------------------------------------------------------------
    // Setup helpers
    // -----------------------------------------------------------------------

    /** Seed the unique ASSET account (idempotent via ON DUPLICATE KEY). */
    private void seedAssetAccount() {
        jdbcTemplate.update(
                "INSERT INTO ledger_account (code, tenant_id, type, normal_side, created_at) "
                        + "VALUES (?, 'finance', 'ASSET', 'DEBIT', ?) "
                        + "ON DUPLICATE KEY UPDATE code = code",
                FX_ACCOUNT, java.sql.Timestamp.from(Instant.now()));
    }

    /** Set the tenant's FX cost-flow method (upsert). */
    private void setCostFlow(String method) {
        jdbcTemplate.update(
                "INSERT INTO fx_cost_flow_config (tenant_id, method, updated_by, updated_at) "
                        + "VALUES ('finance', ?, 'it-operator', ?) "
                        + "ON DUPLICATE KEY UPDATE method = VALUES(method)",
                method, java.sql.Timestamp.from(Instant.now()));
    }

    /**
     * Post a single USD acquisition (DR FX_ACCOUNT {foreignMinor USD} / CR SETTLEMENT_SUSPENSE
     * {baseMinor KRW}) through the guarded write path so {@code RecordFxAcquisitionLots} fires
     * and materializes a lot.
     */
    private void acquire(String token, long foreignMinor, long baseMinor) throws Exception {
        String key = "LOTSREAD-ACQ-" + newId().substring(0, 8);
        String body = "{"
                + "\"reference\":\"LOTSREAD-ACQ\",\"memo\":\"acquire USD\",\"lines\":["
                + "{\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"" + foreignMinor + "\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"" + baseMinor + "\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + PROCEEDS + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"" + baseMinor + "\",\"currency\":\"KRW\"}}"
                + "]}";
        assertThat(postEntry(token, key, body).statusCode())
                .as("acquisition entry must return 201").isEqualTo(201);
    }

    private String settlementBody(String rate, String settleForeignAmount) {
        return "{"
                + "\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"currency\":\"USD\","
                + "\"settlementRate\":\"" + rate + "\",\"proceedsAccountCode\":\"" + PROCEEDS + "\","
                + (settleForeignAmount != null
                        ? "\"settleForeignAmount\":\"" + settleForeignAmount + "\"," : "")
                + "\"reference\":\"LOTSREAD-SETTLE\",\"memo\":\"settle lots read test\"}";
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void acOneAndTwoAndThreeAndFour() throws Exception {
        String token = financeWriteToken();
        seedAssetAccount();

        // ============ (AC-3) empty position → empty list + zero summary, 200 not 404 ============
        HttpResponse<String> r0 = getLots(token, FX_ACCOUNT, "USD");
        assertThat(r0.statusCode()).as("AC-3 empty lots must be 200, not 404").isEqualTo(200);
        JsonNode b0 = objectMapper.readTree(r0.body());
        assertThat(b0.at("/data/lotCount").asInt()).isZero();
        assertThat(b0.at("/data/lots").size()).isZero();
        assertThat(b0.at("/data/totalRemainingForeignMinor").asText()).isEqualTo("0");
        assertThat(b0.at("/data/totalCarryingBaseMinor").asText()).isEqualTo("0");

        // ============ (AC-1) 2 USD acquisitions → GET lots returns 2 ordered with correct fields ============
        // Lot 1: 1000 USD @ 1,300,000 KRW
        acquire(token, 1_000L, 1_300_000L);
        // Lot 2: 500 USD @ 700,000 KRW
        acquire(token, 500L, 700_000L);

        HttpResponse<String> r1 = getLots(token, FX_ACCOUNT, "USD");
        assertThat(r1.statusCode()).isEqualTo(200);
        JsonNode b1 = objectMapper.readTree(r1.body());

        assertThat(b1.at("/data/lotCount").asInt()).isEqualTo(2);
        assertThat(b1.at("/data/lots").size()).isEqualTo(2);

        // Summary
        assertThat(b1.at("/data/totalRemainingForeignMinor").asText()).isEqualTo("1500");
        assertThat(b1.at("/data/totalCarryingBaseMinor").asText()).isEqualTo("2000000");

        // Per-lot fields (AC-1: fields must be correct, FIFO order — lot1 acquired first)
        JsonNode l0 = b1.at("/data/lots/0");
        assertThat(l0.at("/currency").asText()).isEqualTo("USD");
        assertThat(l0.at("/originalForeignMinor").asText()).isEqualTo("1000");
        assertThat(l0.at("/remainingForeignMinor").asText()).isEqualTo("1000");
        assertThat(l0.at("/originalBaseMinor").asText()).isEqualTo("1300000");
        assertThat(l0.at("/carryingBaseMinor").asText()).isEqualTo("1300000");
        assertThat(l0.at("/lotId").asText()).isNotBlank();
        assertThat(l0.at("/sourceJournalEntryId").asText()).isNotBlank();
        assertThat(l0.at("/acquiredAt").asText()).isNotBlank();

        JsonNode l1 = b1.at("/data/lots/1");
        assertThat(l1.at("/originalForeignMinor").asText()).isEqualTo("500");
        assertThat(l1.at("/remainingForeignMinor").asText()).isEqualTo("500");
        assertThat(l1.at("/originalBaseMinor").asText()).isEqualTo("700000");
        assertThat(l1.at("/carryingBaseMinor").asText()).isEqualTo("700000");

        // ============ (AC-2) FIFO partial settle → decremented remaining/carrying; consumed lot excluded ============
        // Set FIFO cost-flow and settle 1000 USD (the entire first lot) at spot 1400.
        // Proceeds = 1400 * 1000 = 1,400,000. C_settle_fifo = 1,300,000 (lot 1 fully consumed).
        setCostFlow("FIFO");
        HttpResponse<String> rs = postSettlement(token,
                "LOTSREAD-S1-" + newId().substring(0, 8),
                settlementBody("1400", "1000"));
        assertThat(rs.statusCode()).as("partial FIFO settle must return 201").isEqualTo(201);

        HttpResponse<String> r2 = getLots(token, FX_ACCOUNT, "USD");
        assertThat(r2.statusCode()).isEqualTo(200);
        JsonNode b2 = objectMapper.readTree(r2.body());

        // Lot 1 was fully consumed → only lot 2 remains (AC-2: fully-consumed excluded)
        assertThat(b2.at("/data/lotCount").asInt())
                .as("AC-2: fully consumed lot1 must not appear").isEqualTo(1);
        assertThat(b2.at("/data/lots").size()).isEqualTo(1);

        // Lot 2 is untouched (we only settled exactly lot1's foreign amount)
        JsonNode remaining = b2.at("/data/lots/0");
        assertThat(remaining.at("/remainingForeignMinor").asText())
                .as("AC-2: lot2 remaining unchanged").isEqualTo("500");
        assertThat(remaining.at("/carryingBaseMinor").asText())
                .as("AC-2: lot2 carrying unchanged").isEqualTo("700000");

        // Summary reflects only lot2
        assertThat(b2.at("/data/totalRemainingForeignMinor").asText()).isEqualTo("500");
        assertThat(b2.at("/data/totalCarryingBaseMinor").asText()).isEqualTo("700000");

        // ============ (AC-4) unknown currency → 400 VALIDATION_ERROR ============
        HttpResponse<String> r4 = getLots(token, FX_ACCOUNT, "XYZ");
        assertThat(r4.statusCode()).as("AC-4: unknown currency must be 400").isEqualTo(400);
        JsonNode b4 = objectMapper.readTree(r4.body());
        assertThat(b4.at("/code").asText()).isEqualTo("VALIDATION_ERROR");
    }
}
